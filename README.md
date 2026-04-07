<div align="center">

```
EM DESENVOLVIMENTO
```

=======
# ⬡ OpenChat

**Interface web local para modelos de linguagem open source via Ollama**

[![Java](https://img.shields.io/badge/Java-17-orange?style=flat-square&logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2-6DB33F?style=flat-square&logo=springboot)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?style=flat-square&logo=postgresql)](https://www.postgresql.org/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=flat-square&logo=docker)](https://www.docker.com/)
[![Ollama](https://img.shields.io/badge/Ollama-Local_AI-000000?style=flat-square)](https://ollama.com/)
[![License](https://img.shields.io/badge/License-MIT-green?style=flat-square)](LICENSE)

Uma interface visual completa para conversar com modelos de linguagem rodando localmente.  
Seus dados ficam no seu hardware. Sem APIs pagas. Sem envio de dados para servidores externos.

</div>

---

## ✨ Funcionalidades

- 💬 **Chat em tempo real** com streaming de respostas token a token
- 🧠 **Memória persistente** — salva fatos sobre o usuário para personalizar respostas
- 📁 **Projetos** — crie espaços de contexto com arquivos PDF, DOCX, TXT e MD
- 📎 **Anexo de arquivos** — envie documentos diretamente no chat
- 👁 **Suporte a modelos Vision** — envie imagens para modelos multimodais
- 💭 **Thinking Mode** — visualize o raciocínio de modelos como DeepSeek e Qwen3
- 📌 **Chats fixados** — fixe até 3 conversas importantes no topo da sidebar
- ✏️ **Renomear conversas** — dê títulos personalizados aos seus chats
- 🏷️ **Tags automáticas** — identifica modelos Cloud, Vision, Think, Tools e Embedding
- 🌗 **Tema claro e escuro** — com cor de ênfase personalizável
- 🌍 **Idioma de resposta** — force o modelo a responder em qualquer idioma
- 🔍 **Busca no histórico** — encontre conversas antigas rapidamente
- ⏹️ **Parar geração** — interrompa a resposta a qualquer momento

---

## 🛠️ Tecnologias

| Camada | Tecnologia |
|---|---|
| Frontend | HTML + CSS + JavaScript (Vanilla) |
| Backend | Java 17 + Spring Boot 3.2 + Spring WebFlux |
| Banco de dados | PostgreSQL 16 |
| IA Local | Ollama |
| Infraestrutura | Docker + Docker Compose |

---

## 📋 Pré-requisitos

Você **não precisa ter Java instalado**. Tudo roda dentro do Docker.

O que você precisa:

- **[Docker](https://docs.docker.com/get-docker/)** com Docker Compose
- **[Ollama](https://ollama.com/download)** instalado e rodando
- Pelo menos **um modelo baixado** no Ollama

---

## 🚀 Instalação

### 1. Clone o repositório

```bash
git clone https://github.com/Otavio2704/OpenChat.git
cd openchat
```

### 2. Configure o ambiente (opcional)

```bash
cp docker-compose.override.yml.example docker-compose.override.yml
```

> Por padrão já funciona sem alterações. O `.override.yml` é para customizações como senha do banco ou porta.

### 3. Baixe um modelo no Ollama
*Exemplo*:
```bash
ollama pull llama3.2
```

**⚠️ Recomendação:** Caso tenha um hardware humilde, é recomendável baixar um modelo cloud
```bash
ollama pull qwen3.5:cloud
```

Baixe os modelos por aqui -> [Modelos do Ollama](https://ollama.com/search).

### 4. Suba os containers

```bash
docker compose up -d
```

Aguarde ~30 segundos e acesse `http://localhost:8080`.

---

## ⚙️ Configuração do Ollama

O Ollama precisa aceitar conexões externas para que o container Docker consiga se comunicar com ele.

### Linux

```bash
sudo mkdir -p /etc/systemd/system/ollama.service.d

sudo tee /etc/systemd/system/ollama.service.d/override.conf > /dev/null << 'EOF'
[Service]
Environment="OLLAMA_HOST=0.0.0.0"
EOF

sudo systemctl daemon-reload
sudo systemctl restart ollama
```

**Importante no Linux:** é necessário liberar a porta do Ollama no firewall para as redes Docker:

```bash
sudo iptables -I INPUT -s 172.18.0.0/16 -p tcp --dport 11434 -j ACCEPT
sudo iptables -I INPUT -s 172.17.0.0/16 -p tcp --dport 11434 -j ACCEPT

# Persiste as regras (sobrevive a reinicializações)
sudo apt install iptables-persistent -y
sudo netfilter-persistent save
```

### macOS

```bash
launchctl setenv OLLAMA_HOST "0.0.0.0"
# Reinicie o Ollama após o comando
```

### Windows (PowerShell)

```powershell
[System.Environment]::SetEnvironmentVariable('OLLAMA_HOST', '0.0.0.0', 'User')
# Reinicie o Ollama após o comando
```

---

## 🐳 Comandos Docker úteis

```bash
# Subir os serviços
docker compose up -d

# Ver logs em tempo real
docker compose logs -f

# Ver logs só do backend
docker compose logs -f backend

# Parar tudo (preserva os dados do banco)
docker compose down

# Parar e apagar todos os dados
docker compose down -v

# Rebuildar após mudanças no código
docker compose up -d --build
```

---

## 📁 Estrutura do Projeto

```
openchat/
├── backend/
│   ├── src/main/java/com/seunome/ollamachat/
│   │   ├── config/       # CORS, WebClient
│   │   ├── controller/   # Chat, History, Models, Files, Memory, Projects
│   │   ├── service/      # OllamaService, ConversationService, MemoryService, ProjectService
│   │   ├── repository/   # JPA Repositories
│   │   └── model/        # Entidades JPA + DTOs
│   ├── Dockerfile
│   └── pom.xml
├── frontend/
│   ├── index.html
│   ├── style.css
│   └── app.js
├── docker-compose.yml
├── docker-compose.override.yml.example
└── README.md
```

---

## 🧠 Como funciona a Memória

1. Clique no ícone 🧠 na topbar
2. Adicione fatos: *"Prefiro respostas diretas"*, *"Trabalho com Java e Spring Boot"*
3. Organize por categoria: Preferência, Contexto, Habilidade, Projeto
4. Ative ou desative memórias individualmente

Todas as memórias ativas são injetadas automaticamente no contexto de cada conversa.

---

## 📂 Como funcionam os Projetos

1. Na sidebar, clique em **+** ao lado de "Projetos"
2. Dê um nome e descrição ao projeto
3. Adicione arquivos (PDF, DOCX, TXT, MD) ou textos livres
4. Clique em **"Iniciar chat com este projeto"**

O conteúdo dos arquivos é injetado automaticamente no contexto de cada mensagem enviada.

---

## 🤝 Contribuindo

Contribuições são bem-vindas!

1. Fork o repositório
2. Crie uma branch: `git checkout -b feature/minha-feature`
3. Commit: `git commit -m 'feat: adiciona minha feature'`
4. Push: `git push origin feature/minha-feature`
5. Abra um Pull Request

---

## 📄 Licença

Este projeto está sob a licença MIT. Veja o arquivo [LICENSE](LICENSE) para mais detalhes.

---

<div align="center">
Feito por Otávio Guedes <code>Dev Backend Java</code>
</div>
