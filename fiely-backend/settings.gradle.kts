rootProject.name = "fiely-backend"

include("fiely-plugin-api")
include("fiely-core")

// First-party plugins
include("plugins:fiely-auth-jwt")
include("plugins:fiely-auth-oidc")
include("plugins:fiely-auth-ldap")
include("plugins:fiely-storage-local")
include("plugins:fiely-ai-ollama")
include("plugins:fiely-ai-openai")
include("plugins:fiely-ai-claude")
include("plugins:fiely-processor-text")
include("plugins:fiely-notify-email")
