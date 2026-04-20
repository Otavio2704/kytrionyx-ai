#!/bin/bash
# deploy-kyronai.sh
# Deploy automático seguindo a arquitetura do Kyron AI
# Execute a partir da raiz do projeto: bash deploy-kyronai.sh

set -e

OUTPUTS="$(dirname "$0")"  # diretório onde este script está
PROJECT="$(pwd)"            # raiz do projeto kyron-ai
BASE_PATH="$PROJECT/backend/src/main/java/otavio/kyronai"

echo "📂 Projeto: $PROJECT"
echo "📦 Origem:  $OUTPUTS"
echo ""

# Função para copiar arquivo com validação
copy_file() {
    local src="$1"
    local dest="$2"
    
    if [ ! -f "$src" ]; then
        echo "⚠️  AVISO: Arquivo não encontrado: $src"
        return 1
    fi
    
    mkdir -p "$(dirname "$dest")"
    cp "$src" "$dest"
    echo "   ✓ $(basename "$dest")"
}

# ── AGENT ────────────────────────────────────────────────────────
AGENT_DIR="$BASE_PATH/agent"
echo "→ Copiando Agent..."
copy_file "$OUTPUTS/backend/src/main/java/otavio/kyronai/agent/AgentAction.java" "$AGENT_DIR/AgentAction.java"
copy_file "$OUTPUTS/backend/src/main/java/otavio/kyronai/agent/AgentActionDTO.java" "$AGENT_DIR/AgentActionDTO.java"
copy_file "$OUTPUTS/backend/src/main/java/otavio/kyronai/agent/AgentActionRepository.java" "$AGENT_DIR/AgentActionRepository.java"
copy_file "$OUTPUTS/backend/src/main/java/otavio/kyronai/agent/AgentController.java" "$AGENT_DIR/AgentController.java"
copy_file "$OUTPUTS/backend/src/main/java/otavio/kyronai/agent/AgentService.java" "$AGENT_DIR/AgentService.java"

# ── CHAT ─────────────────────────────────────────────────────────
CHAT_DIR="$BASE_PATH/chat"
echo "→ Copiando Chat..."
copy_file "$OUTPUTS/backend/src/main/java/otavio/kyronai/chat/ChatController.java" "$CHAT_DIR/ChatController.java"
copy_file "$OUTPUTS/backend/src/main/java/otavio/kyronai/chat/Conversation.java" "$CHAT_DIR/Conversation.java"
copy_file "$OUTPUTS/backend/src/main/java/otavio/kyronai/chat/ConversationDTO.java" "$CHAT_DIR/ConversationDTO.java"
copy_file "$OUTPUTS/backend/src/main/java/otavio/kyronai/chat/ConversationRepository.java" "$CHAT_DIR/ConversationRepository.java"
copy_file "$OUTPUTS/backend/src/main/java/otavio/kyronai/chat/ConversationService.java" "$CHAT_DIR/ConversationService.java"
copy_file "$OUTPUTS/backend/src/main/java/otavio/kyronai/chat/HistoryController.java" "$CHAT_DIR/HistoryController.java"
copy_file "$OUTPUTS/backend/src/main/java/otavio/kyronai/chat/Message.java" "$CHAT_DIR/Message.java"
copy_file "$OUTPUTS/backend/src/main/java/otavio/kyronai/chat/MessageRepository.java" "$CHAT_DIR/MessageRepository.java"

# ── CODE ─────────────────────────────────────────────────────────
CODE_DIR="$BASE_PATH/code"
echo "→ Copiando Code..."
copy_file "$OUTPUTS/backend/src/main/java/otavio/kyronai/code/CodeController.java" "$CODE_DIR/CodeController.java"
copy_file "$OUTPUTS/backend/src/main/java/otavio/kyronai/code/CodeGenerationService.java" "$CODE_DIR/CodeGenerationService.java"
copy_file "$OUTPUTS/backend/src/main/java/otavio/kyronai/code/CodeSession.java" "$CODE_DIR/CodeSession.java"
copy_file "$OUTPUTS/backend/src/main/java/otavio/kyronai/code/CodeSessionDTO.java" "$CODE_DIR/CodeSessionDTO.java"
copy_file "$OUTPUTS/backend/src/main/java/otavio/kyronai/code/CodeSessionRepository.java" "$CODE_DIR/CodeSessionRepository.java"
copy_file "$OUTPUTS/backend/src/main/java/otavio/kyronai/code/GeneratedFile.java" "$CODE_DIR/GeneratedFile.java"
copy_file "$OUTPUTS/backend/src/main/java/otavio/kyronai/code/GeneratedFileRepository.java" "$CODE_DIR/GeneratedFileRepository.java"

# ── CONFIG ───────────────────────────────────────────────────────
CONFIG_DIR="$BASE_PATH/config"
echo "→ Copiando Config..."
copy_file "$OUTPUTS/backend/src/main/java/otavio/kyronai/config/CorsConfig.java" "$CONFIG_DIR/CorsConfig.java"
copy_file "$OUTPUTS/backend/src/main/java/otavio/kyronai/config/WebClientConfig.java" "$CONFIG_DIR/WebClientConfig.java"

# ── FILES ────────────────────────────────────────────────────────
FILES_DIR="$BASE_PATH/files"
echo "→ Copiando Files..."
copy_file "$OUTPUTS/backend/src/main/java/otavio/kyronai/files/FileController.java" "$FILES_DIR/FileController.java"
copy_file "$OUTPUTS/backend/src/main/java/otavio/kyronai/files/FileExtractorService.java" "$FILES_DIR/FileExtractorService.java"

# ── GITHUB ───────────────────────────────────────────────────────
GITHUB_DIR="$BASE_PATH/github"
echo "→ Copiando GitHub..."
copy_file "$OUTPUTS/backend/src/main/java/otavio/kyronai/github/GitHubController.java" "$GITHUB_DIR/GitHubController.java"
copy_file "$OUTPUTS/backend/src/main/java/otavio/kyronai/github/GitHubRepo.java" "$GITHUB_DIR/GitHubRepo.java"
copy_file "$OUTPUTS/backend/src/main/java/otavio/kyronai/github/GitHubRepoDTO.java" "$GITHUB_DIR/GitHubRepoDTO.java"
copy_file "$OUTPUTS/backend/src/main/java/otavio/kyronai/github/GitHubRepoStore.java" "$GITHUB_DIR/GitHubRepoStore.java"
copy_file "$OUTPUTS/backend/src/main/java/otavio/kyronai/github/GitHubService.java" "$GITHUB_DIR/GitHubService.java"

# ── MEMORY ───────────────────────────────────────────────────────
MEMORY_DIR="$BASE_PATH/memory"
echo "→ Copiando Memory..."
copy_file "$OUTPUTS/backend/src/main/java/otavio/kyronai/memory/Memory.java" "$MEMORY_DIR/Memory.java"
copy_file "$OUTPUTS/backend/src/main/java/otavio/kyronai/memory/MemoryController.java" "$MEMORY_DIR/MemoryController.java"
copy_file "$OUTPUTS/backend/src/main/java/otavio/kyronai/memory/MemoryRepository.java" "$MEMORY_DIR/MemoryRepository.java"
copy_file "$OUTPUTS/backend/src/main/java/otavio/kyronai/memory/MemoryService.java" "$MEMORY_DIR/MemoryService.java"

# ── MODEL ────────────────────────────────────────────────────────
MODEL_DIR="$BASE_PATH/model"
echo "→ Copiando Model..."
copy_file "$OUTPUTS/backend/src/main/java/otavio/kyronai/model/ModelCapabilities.java" "$MODEL_DIR/ModelCapabilities.java"
copy_file "$OUTPUTS/backend/src/main/java/otavio/kyronai/model/ModelController.java" "$MODEL_DIR/ModelController.java"

# ── OLLAMA ───────────────────────────────────────────────────────
OLLAMA_DIR="$BASE_PATH/ollama"
echo "→ Copiando Ollama..."
copy_file "$OUTPUTS/backend/src/main/java/otavio/kyronai/ollama/OllamaService.java" "$OLLAMA_DIR/OllamaService.java"

# ── PROJECT ──────────────────────────────────────────────────────
PROJECT_DIR="$BASE_PATH/project"
echo "→ Copiando Project..."
copy_file "$OUTPUTS/backend/src/main/java/otavio/kyronai/project/Project.java" "$PROJECT_DIR/Project.java"
copy_file "$OUTPUTS/backend/src/main/java/otavio/kyronai/project/ProjectController.java" "$PROJECT_DIR/ProjectController.java"
copy_file "$OUTPUTS/backend/src/main/java/otavio/kyronai/project/ProjectDTO.java" "$PROJECT_DIR/ProjectDTO.java"
copy_file "$OUTPUTS/backend/src/main/java/otavio/kyronai/project/ProjectFile.java" "$PROJECT_DIR/ProjectFile.java"
copy_file "$OUTPUTS/backend/src/main/java/otavio/kyronai/project/ProjectFileRepository.java" "$PROJECT_DIR/ProjectFileRepository.java"
copy_file "$OUTPUTS/backend/src/main/java/otavio/kyronai/project/ProjectRepository.java" "$PROJECT_DIR/ProjectRepository.java"
copy_file "$OUTPUTS/backend/src/main/java/otavio/kyronai/project/ProjectService.java" "$PROJECT_DIR/ProjectService.java"

# ── APPLICATION ──────────────────────────────────────────────────
echo "→ Copiando Application..."
copy_file "$OUTPUTS/backend/src/main/java/otavio/kyronai/KyronAiApplication.java" "$BASE_PATH/KyronAiApplication.java"

# ── FRONTEND (STATIC) ────────────────────────────────────────────
STATIC_DIR="$PROJECT/backend/src/main/resources/static"
echo "→ Copiando Frontend..."
mkdir -p "$STATIC_DIR"
copy_file "$OUTPUTS/frontend/app.js" "$STATIC_DIR/app.js"
copy_file "$OUTPUTS/frontend/index.html" "$STATIC_DIR/index.html"
copy_file "$OUTPUTS/frontend/style.css" "$STATIC_DIR/style.css"

# ── CONFIGURATION ────────────────────────────────────────────────
RESOURCES_DIR="$PROJECT/backend/src/main/resources"
echo "→ Copiando Configurações..."
copy_file "$OUTPUTS/backend/src/main/resources/application.yml" "$RESOURCES_DIR/application.yml"

echo ""
echo "✅ Todos os arquivos copiados com sucesso!"
echo ""
echo "Agora execute:"
echo "  docker compose down && docker compose up -d --build"
echo ""