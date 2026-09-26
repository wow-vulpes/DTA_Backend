package ru.dta.check.api;

import java.util.ArrayList;
import java.util.List;
import java.time.Instant;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import ru.dta.check.application.CheckService;
import ru.dta.check.domain.MaterialMetadata;
import ru.dta.check.domain.RecordType;

@RestController
@RequiredArgsConstructor
public class CheckController {

    private static final long MAX_FILE_BYTES = 25L * 1024 * 1024;

    private final CheckService checkService;
    private final CheckResponseMapper responseMapper;

    @PostMapping(value = "/api/checks", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public CheckResponse createCheck(@RequestParam(name = "record_type", required = false) String recordType,
            @RequestParam(name = "files", required = false) List<MultipartFile> files) {
        List<FieldErrorResponse> errors = new ArrayList<>();
        RecordType type = switch (recordType == null ? "" : recordType) {
            case "daily" -> RecordType.DAILY;
            case "weekly" -> RecordType.WEEKLY;
            default -> null;
        };
        if (type == null) {
            errors.add(new FieldErrorResponse("record_type", "Допустимые значения: daily, weekly."));
        }
        if (files == null || files.isEmpty() || files.size() > 20) {
            errors.add(new FieldErrorResponse("files", "Требуется от 1 до 20 файлов."));
        }
        List<MaterialMetadata> materials = new ArrayList<>();
        if (files != null) {
            for (int index = 0; index < files.size(); index++) {
                MultipartFile file = files.get(index);
                if (file.getSize() > MAX_FILE_BYTES) {
                    throw new MaxUploadSizeExceededException(MAX_FILE_BYTES);
                }
                String name = file.getOriginalFilename();
                if (!validFilename(name)) {
                    errors.add(new FieldErrorResponse("files[" + index + "]",
                            "Имя должно содержать 1–255 символов без управляющих символов и разделителей пути."));
                } else {
                    materials.add(new MaterialMetadata(name, file.getSize()));
                }
            }
        }
        if (!errors.isEmpty()) {
            throw new InvalidCheckRequestException(errors);
        }
        return responseMapper.toResponse(checkService.createCheck(type, materials));
    }

    @GetMapping(value = "/api/checks", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional(readOnly = true)
    public CheckListResponse findChecks(@RequestParam(name = "record_type", required = false) String recordType,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "from", required = false) Instant from,
            @RequestParam(name = "to", required = false) Instant to,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100 || (from != null && to != null && !from.isBefore(to))) {
            throw new InvalidCheckRequestException(List.of(
                    new FieldErrorResponse("query", "Проверьте page, size, from и to.")));
        }
        return responseMapper.toListResponse(checkService.findChecks(
                new CheckQuery(recordType, status, from, to, page, size)));
    }

    @GetMapping(value = "/api/checks/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional(readOnly = true)
    public CheckResponse getCheck(@PathVariable("id") UUID id) {
        return responseMapper.toResponse(checkService.getCheck(id));
    }

    private boolean validFilename(String name) {
        return name != null && !name.isBlank() && name.codePointCount(0, name.length()) <= 255
                && name.indexOf('/') < 0 && name.indexOf('\\') < 0
                && name.codePoints().noneMatch(Character::isISOControl);
    }
}
