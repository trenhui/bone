package scaffold

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"github.com/bone-engine/bone-extension-go/api/model"
)

// ExtensionScaffoldGenerator 扩展脚手架生成器
type ExtensionScaffoldGenerator struct {
	outputDir string
}

// NewExtensionScaffoldGenerator 创建扩展脚手架生成器
func NewExtensionScaffoldGenerator(outputDir string) *ExtensionScaffoldGenerator {
	if outputDir == "" {
		outputDir = "./extensions"
	}

	return &ExtensionScaffoldGenerator{
		outputDir: outputDir,
	}
}

// GenerateExtensionScaffold 生成扩展脚手架
func (g *ExtensionScaffoldGenerator) GenerateExtensionScaffold(metadata *model.ExtensionDefinition) error {
	if metadata == nil {
		return fmt.Errorf("extension metadata cannot be nil")
	}

	// 创建输出目录
	if err := os.MkdirAll(g.outputDir, 0755); err != nil {
		return err
	}

	// 生成扩展文件
	extDir := filepath.Join(g.outputDir, metadata.Name)
	if err := os.MkdirAll(extDir, 0755); err != nil {
		return err
	}

	// 生成main.go
	if err := g.generateMainFile(extDir, metadata); err != nil {
		return err
	}

	// 生成go.mod
	if err := g.generateGoModFile(extDir, metadata); err != nil {
		return err
	}

	// 生成README.md
	if err := g.generateReadmeFile(extDir, metadata); err != nil {
		return err
	}

	return nil
}

// generateMainFile 生成main.go文件
func (g *ExtensionScaffoldGenerator) generateMainFile(dir string, metadata *model.ExtensionDefinition) error {
	content := fmt.Sprintf(`package main

import (
	"context"
	"github.com/bone-engine/bone-extension-go/extension"
)

// NewExtension 创建扩展实例
func NewExtension() *extension.Extension {
	return extension.New("%s", "%s", func(ctx *extension.Context) error {
		// 扩展逻辑
		// 示例：从上下文获取数据
		// if value, ok := ctx.Get("key"); ok {
		//     // 处理数据
		// }
		
		// 设置结果
		// ctx.Result = "result"
		
		return nil
	}).WithName("%s").WithDescription("%s").WithPriority(%d)
}

func main() {
	// 扩展入口点
	_ = NewExtension()
}
`, metadata.ID, metadata.Point, metadata.Name, metadata.Description, metadata.Priority)

	filePath := filepath.Join(dir, "main.go")
	return os.WriteFile(filePath, []byte(content), 0644)
}

// generateGoModFile 生成go.mod文件
func (g *ExtensionScaffoldGenerator) generateGoModFile(dir string, metadata *model.ExtensionDefinition) error {
	content := fmt.Sprintf(`module %s

go 1.21

require (
	github.com/bone-engine/bone-extension-go v0.1.0
)

replace github.com/bone-engine/bone-extension-go => ../..
`, metadata.ID)

	filePath := filepath.Join(dir, "go.mod")
	return os.WriteFile(filePath, []byte(content), 0644)
}

// generateReadmeFile 生成README.md文件
func (g *ExtensionScaffoldGenerator) generateReadmeFile(dir string, metadata *model.ExtensionDefinition) error {
	content := fmt.Sprintf(`# %s

## Description

%s

## Extension Point

%s

## Priority

%d

## Usage

```bash
# 构建扩展
go build -buildmode=plugin -o %s.so

# 加载扩展
# 在主应用中使用 extension.LoadExtension("%s.so")
```

## Development

1. 编辑 `main.go` 文件，实现扩展逻辑
2. 构建扩展
3. 在主应用中加载和使用扩展
`, metadata.Name, metadata.Description, metadata.Point, metadata.Priority, metadata.ID, metadata.ID)

	filePath := filepath.Join(dir, "README.md")
	return os.WriteFile(filePath, []byte(content), 0644)
}

// GenerateExtensionPointScaffold 生成扩展点脚手架
func (g *ExtensionScaffoldGenerator) GenerateExtensionPointScaffold(metadata *model.ExtensionPointDefinition) error {
	if metadata == nil {
		return fmt.Errorf("extension point metadata cannot be nil")
	}

	// 创建输出目录
	if err := os.MkdirAll(g.outputDir, 0755); err != nil {
		return err
	}

	// 生成扩展点文件
	pointDir := filepath.Join(g.outputDir, metadata.Name)
	if err := os.MkdirAll(pointDir, 0755); err != nil {
		return err
	}

	// 生成README.md
	if err := g.generateExtensionPointReadmeFile(pointDir, metadata); err != nil {
		return err
	}

	// 生成示例扩展
	exampleExtDir := filepath.Join(pointDir, "example-extension")
	if err := os.MkdirAll(exampleExtDir, 0755); err != nil {
		return err
	}

	// 生成示例扩展的main.go
	exampleExt := &model.ExtensionDefinition{
		ID:          fmt.Sprintf("%s-example", metadata.Name),
		Name:        fmt.Sprintf("%s Example", metadata.Name),
		Description: fmt.Sprintf("Example extension for %s", metadata.Name),
		Point:       metadata.Name,
		Priority:    100,
	}

	if err := g.generateMainFile(exampleExtDir, exampleExt); err != nil {
		return err
	}

	if err := g.generateGoModFile(exampleExtDir, exampleExt); err != nil {
		return err
	}

	return nil
}

// generateExtensionPointReadmeFile 生成扩展点README.md文件
func (g *ExtensionScaffoldGenerator) generateExtensionPointReadmeFile(dir string, metadata *model.ExtensionPointDefinition) error {
	content := fmt.Sprintf(`# Extension Point: %s

## Description

%s

## Parameters

`, metadata.Name, metadata.Description)

	// 添加参数信息
	if len(metadata.Parameters) > 0 {
		content += "| Name | Type | Description |\n"
		content += "|------|------|-------------|\n"
		for _, param := range metadata.Parameters {
			content += fmt.Sprintf("| %s | %s | %s |\n", param.Name, param.Type, param.Description)
		}
		content += "\n"
	}

	content += "## Returns\n\n"
	content += fmt.Sprintf("| Type | Description |\n")
	content += fmt.Sprintf("|------|-------------|\n")
	content += fmt.Sprintf("| %s | %s |\n", metadata.Returns.Type, metadata.Returns.Description)

	content += "\n## Example Extension\n\n"
	content += "See `example-extension` directory for an example implementation."

	filePath := filepath.Join(dir, "README.md")
	return os.WriteFile(filePath, []byte(content), 0644)
}

// GenerateAllScaffolds 生成所有脚手架
func (g *ExtensionScaffoldGenerator) GenerateAllScaffolds(extensions []*model.ExtensionDefinition, points []*model.ExtensionPointDefinition) error {
	for _, ext := range extensions {
		if err := g.GenerateExtensionScaffold(ext); err != nil {
			return err
		}
	}

	for _, point := range points {
		if err := g.GenerateExtensionPointScaffold(point); err != nil {
			return err
		}
	}

	return nil
}
