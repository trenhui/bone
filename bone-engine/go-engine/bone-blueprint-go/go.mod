module github.com/bone-engine/bone-blueprint-go

go 1.21

require (
	github.com/gin-gonic/gin v1.9.1
	github.com/golang-jwt/jwt/v5 v5.2.0
	github.com/joho/godotenv v1.5.1
	github.com/mattn/go-sqlite3 v1.14.17
	github.com/stretchr/testify v1.8.4
	github.com/bone-engine/bone-extension-go v0.1.0
	github.com/bone-engine/bone-metadata-go v0.1.0
)

replace (
	github.com/bone-engine/bone-extension-go => ../bone-extension-go
	github.com/bone-engine/bone-metadata-go => ../bone-metadata-go
)
