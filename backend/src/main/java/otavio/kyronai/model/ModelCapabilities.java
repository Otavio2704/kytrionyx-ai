package otavio.kyronai.models;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ModelCapabilities {

    private String  modelName;
    private boolean supportsThinking;
    private boolean supportsVision;
    private int     contextLength;
    private String  family;
    private String  parameterSize;

    public List<String> getAcceptedFileTypes() {
        List<String> types = new ArrayList<>(List.of("txt", "md", "pdf", "docx"));
        if (supportsVision) types.addAll(List.of("jpg", "jpeg", "png", "webp", "gif"));
        return types;
    }
}