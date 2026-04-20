package otavio.kyronai.files;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileExtractorService fileExtractorService;

    @PostMapping("/extract")
    public ResponseEntity<Map<String, Object>> extract(
            @RequestParam("file") MultipartFile file) {
        if (file.isEmpty())
            return ResponseEntity.badRequest().body(Map.of("error", "Arquivo vazio"));

        String filename = file.getOriginalFilename() != null
                ? file.getOriginalFilename().toLowerCase() : "";
        String ext = filename.contains(".")
                ? filename.substring(filename.lastIndexOf('.') + 1) : "";

        try {
            String text = fileExtractorService.extract(file, ext);
            if (text.length() > 100_000)
                text = text.substring(0, 100_000) + "\n\n[... truncado ...]";
            return ResponseEntity.ok(
                    Map.of("text", text, "filename", filename, "chars", text.length()));
        } catch (Exception e) {
            log.error("Erro ao extrair '{}': {}", filename, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}