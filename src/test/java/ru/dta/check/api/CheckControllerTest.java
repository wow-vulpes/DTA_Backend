package ru.dta.check.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import ru.dta.check.application.CheckService;
import ru.dta.check.domain.MaterialMetadata;
import ru.dta.check.domain.MaterialTypeDetector;
import ru.dta.check.domain.RecordChecker;
import ru.dta.check.domain.RecordType;
import ru.dta.check.persistence.CheckEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CheckControllerTest {

    private final CheckService service = mock(CheckService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new CheckController(service,
                Mappers.getMapper(CheckResponseMapper.class))).setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"daily", "weekly"})
    void returnsPersistedResultWithPublicJsonContract(String type) throws Exception {
        UUID id = UUID.randomUUID();
        RecordType recordType = type.equals("daily") ? RecordType.DAILY : RecordType.WEEKLY;
        List<MaterialMetadata> materials = List.of(new MaterialMetadata("scan.jpg", 1536));
        CheckEntity entity = CheckEntity.fromResult(recordType,
                new RecordChecker(new MaterialTypeDetector()).check(recordType, materials),
                Instant.parse("2026-09-26T12:00:00Z"), "Комплект неполный", "Недостаточно материалов.");
        ReflectionTestUtils.setField(entity, "id", id);
        when(service.createCheck(eq(recordType), any())).thenReturn(entity);
        mvc.perform(multipart("/api/checks").file(file("scan.jpg", 1536)).param("record_type", type))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.check_id").value(id.toString()))
                .andExpect(jsonPath("$.status").value("incomplete"))
                .andExpect(jsonPath("$.checked_at").value("2026-09-26T12:00:00Z"))
                .andExpect(jsonPath("$.extracted").value(nullValue()))
                .andExpect(jsonPath("$.documents[0].name").value("scan.jpg"))
                .andExpect(jsonPath("$.documents[0].size_kb").value(1.50))
                .andExpect(jsonPath("$.documents[0].detected_type").value(nullValue()))
                .andExpect(jsonPath("$.issues[0].level").value("warning"))
                .andExpect(jsonPath("$.issues[0].document_index").doesNotExist())
                .andExpect(jsonPath("$.issues[1].level").value("error"));
        verify(service).createCheck(recordType, materials);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"DAILY", "monthly", " daily "})
    void rejectsInvalidRecordType(String value) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/checks").file(file("scan.jpg", 1));
        if (value != null) {
            request.param("record_type", value);
        }
        mvc.perform(request).andExpect(status().is(422))
                .andExpect(jsonPath("$.field_errors[0].field").value("record_type"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @MethodSource("invalidNames")
    void rejectsInvalidNames(String name) throws Exception {
        mvc.perform(multipart("/api/checks").file(file(name, 1)).param("record_type", "daily"))
                .andExpect(status().is(422)).andExpect(jsonPath("$.field_errors[0].field").value("files[0]"));
        verifyNoInteractions(service);
    }

    static Stream<String> invalidNames() {
        return Stream.of("", "  ", "../diary.pdf", "dir\\diary.pdf", "a\nb.pdf", "a\u0000.pdf",
                "a".repeat(256), "😀".repeat(256));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 21})
    void rejectsFileCount(int count) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/checks").param("record_type", "daily");
        for (int index = 0; index < count; index++) {
            request.file(file("scan.jpg", 1));
        }
        mvc.perform(request).andExpect(status().is(422)).andExpect(jsonPath("$.field_errors", hasSize(1)));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 20 * 1024 * 1024 + 1, 25 * 1024 * 1024})
    void acceptsBusinessBoundarySizes(int size) throws Exception {
        when(service.createCheck(any(), any())).thenReturn(result());
        mvc.perform(multipart("/api/checks").file(file("scan.jpg", size)).param("record_type", "daily"))
                .andExpect(status().isCreated());
        verify(service).createCheck(RecordType.DAILY, List.of(new MaterialMetadata("scan.jpg", size)));
    }

    @Test
    void acceptsTwentyFilesAndUnicodeNameBoundary() throws Exception {
        when(service.createCheck(any(), any())).thenReturn(result());
        MockMultipartHttpServletRequestBuilder request = multipart("/api/checks").param("record_type", "daily");
        for (int index = 0; index < 20; index++) {
            request.file(file("😀".repeat(251) + ".pdf", 1));
        }
        mvc.perform(request).andExpect(status().isCreated());
    }

    @Test
    void rejectsTechnicalFileLimit() throws Exception {
        mvc.perform(multipart("/api/checks").file(file("scan.jpg", 25 * 1024 * 1024 + 1))
                .param("record_type", "daily")).andExpect(status().is(413));
        verifyNoInteractions(service);
    }

    @Test
    void rejectsWrongContentType() throws Exception {
        mvc.perform(post("/api/checks").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("unsupported_media_type"));
        verifyNoInteractions(service);
    }

    @Test
    void listsChecksWithQueryParameters() throws Exception {
        when(service.findChecks(any())).thenReturn(new ru.dta.check.application.CheckListPage(List.of(), 1, 10, 0));
        mvc.perform(get("/api/checks").param("record_type", "daily").param("status", "complete")
                        .param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.total").value(0));
        verify(service).findChecks(any());
    }

    @Test
    void rejectsInvalidPageParameters() throws Exception {
        mvc.perform(get("/api/checks").param("page", "-1"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("validation_error"));
        verifyNoInteractions(service);
    }

    @Test
    void returnsFullDetail() throws Exception {
        UUID id = UUID.randomUUID();
        CheckEntity entity = result();
        ReflectionTestUtils.setField(entity, "id", id);
        when(service.getCheck(id)).thenReturn(entity);
        mvc.perform(get("/api/checks/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.check_id").value(id.toString()))
                .andExpect(jsonPath("$.status").value("incomplete"));
        verify(service).getCheck(id);
    }

    @Test
    void returnsNotFoundForMissingDetail() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.getCheck(id)).thenThrow(new CheckNotFoundException(id));
        mvc.perform(get("/api/checks/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("check_not_found"));
    }

    @ParameterizedTest
    @MethodSource("failures")
    void translatesFailuresWithoutLeakingDetails(Failure failure) throws Exception {
        when(service.createCheck(any(), any())).thenThrow(failure.exception());
        String body = mvc.perform(multipart("/api/checks").file(file("scan.jpg", 1))
                .param("record_type", "daily")).andExpect(status().is(failure.status()))
                .andExpect(jsonPath("$.code").value(failure.code()))
                .andExpect(jsonPath("$.field_errors").isArray()).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("secret", "stackTrace", "SQLException");
    }

    static Stream<Failure> failures() {
        return Stream.of(new Failure(new DataAccessResourceFailureException("secret"), 503, "database_unavailable"),
                new Failure(new IllegalStateException("secret"), 500, "internal_error"),
                new Failure(new MultipartException("secret"), 400, "invalid_multipart"),
                new Failure(new MaxUploadSizeExceededException(1), 413, "payload_too_large"));
    }

    record Failure(RuntimeException exception, int status, String code) {
    }

    private CheckEntity result() {
        return CheckEntity.fromResult(RecordType.DAILY,
                new RecordChecker(new MaterialTypeDetector()).check(RecordType.DAILY, List.of()),
                Instant.EPOCH, "Комплект неполный", "Нет материалов.");
    }

    private MockMultipartFile file(String name, int size) {
        return new MockMultipartFile("files", name, "application/octet-stream", new byte[0]) {
            @Override
            public long getSize() {
                return size;
            }
        };
    }
}
