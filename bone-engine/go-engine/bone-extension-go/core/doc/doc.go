package doc

import (
	"context"
	"fmt"
	"reflect"
	"strings"

	"github.com/bone-engine/bone-extension-go/api/model"
	"github.com/bone-engine/bone-extension-go/api/spi"
	"github.com/bone-engine/bone-extension-go/extension"
)

// ExtensionDocGenerator 扩展文档生成器
type ExtensionDocGenerator struct {
	register spi.ExtensionPointRegister
}

// NewExtensionDocGenerator 创建扩展文档生成器
func NewExtensionDocGenerator(register spi.ExtensionPointRegister) *ExtensionDocGenerator {
	return &ExtensionDocGenerator{
		register: register,
	}
}

// GenerateExtensionDoc 生成扩展文档
func (g *ExtensionDocGenerator) GenerateExtensionDoc(ctx context.Context, ext spi.Extension) (*model.ExtensionDocMetadata, error) {
	if ext == nil {
		return nil, fmt.Errorf("extension cannot be nil")
	}

	doc := &model.ExtensionDocMetadata{
		Name:        ext.Name(),
		Description: "",
		Point:       "",
		Priority:    ext.Priority(),
		Parameters:  []model.ParameterMetadata{},
		Returns:     model.ParameterMetadata{},
	}

	// 从扩展实现中提取信息
	if pointProvider, ok := ext.(interface{ Point() string }); ok {
		doc.Point = pointProvider.Point()
	}

	// 提取参数和返回值信息
	g.extractParameters(ext, doc)

	return doc, nil
}

// GenerateExtensionPointDoc 生成扩展点文档
func (g *ExtensionDocGenerator) GenerateExtensionPointDoc(ctx context.Context, point string) (*model.ExtensionPointDocMetadata, error) {
	if point == "" {
		return nil, fmt.Errorf("extension point cannot be empty")
	}

	doc := &model.ExtensionPointDocMetadata{
		Name:        point,
		Description: "",
		Extensions:  []model.ExtensionDocMetadata{},
		Parameters:  []model.ParameterMetadata{},
		Returns:     model.ParameterMetadata{},
	}

	// 获取扩展点的扩展列表
	exts := g.register.GetByPoint(point)
	for _, ext := range exts {
		extDoc, err := g.GenerateExtensionDoc(ctx, ext)
		if err == nil {
			doc.Extensions = append(doc.Extensions, *extDoc)
		}
	}

	return doc, nil
}

// GenerateAllDocs 生成所有文档
func (g *ExtensionDocGenerator) GenerateAllDocs(ctx context.Context) (map[string]*model.ExtensionPointDocMetadata, error) {
	docs := make(map[string]*model.ExtensionPointDocMetadata)

	// 获取所有扩展点
	points := g.register.ListExtensionPoints()
	for _, point := range points {
		doc, err := g.GenerateExtensionPointDoc(ctx, point)
		if err == nil {
			docs[point] = doc
		}
	}

	return docs, nil
}

// extractParameters 提取参数信息
func (g *ExtensionDocGenerator) extractParameters(ext spi.Extension, doc *model.ExtensionDocMetadata) {
	// 这里可以通过反射分析扩展实现的方法签名
	// 简化实现，实际项目中可以更详细
	
	// 检查是否是函数类型
	switch impl := ext.(type) {
	case *extension.Extension:
		// 分析 Handler 函数
		handlerType := reflect.TypeOf(impl.Handler)
		if handlerType.Kind() == reflect.Func {
			// 分析参数
			for i := 0; i < handlerType.NumIn(); i++ {
				paramType := handlerType.In(i)
				param := model.ParameterMetadata{
					Name:        fmt.Sprintf("param%d", i),
					Type:        paramType.String(),
					Description: "",
				}
				doc.Parameters = append(doc.Parameters, param)
			}

			// 分析返回值
			if handlerType.NumOut() > 0 {
				returnType := handlerType.Out(0)
				doc.Returns = model.ParameterMetadata{
					Name:        "result",
					Type:        returnType.String(),
					Description: "",
				}
			}
		}
	}
}

// GenerateMarkdown 生成Markdown文档
func (g *ExtensionDocGenerator) GenerateMarkdown(ctx context.Context, doc *model.ExtensionDocMetadata) string {
	var sb strings.Builder

	sb.WriteString(fmt.Sprintf("# Extension: %s\n\n", doc.Name))
	sb.WriteString(fmt.Sprintf("## Description\n\n%s\n\n", doc.Description))
	sb.WriteString(fmt.Sprintf("## Extension Point\n\n%s\n\n", doc.Point))
	sb.WriteString(fmt.Sprintf("## Priority\n\n%d\n\n", doc.Priority))

	if len(doc.Parameters) > 0 {
		sb.WriteString("## Parameters\n\n")
		sb.WriteString("| Name | Type | Description |\n")
		sb.WriteString("|------|------|-------------|\n")
		for _, param := range doc.Parameters {
			sb.WriteString(fmt.Sprintf("| %s | %s | %s |\n", param.Name, param.Type, param.Description))
		}
		sb.WriteString("\n")
	}

	sb.WriteString("## Returns\n\n")
	sb.WriteString("| Name | Type | Description |\n")
	sb.WriteString("|------|------|-------------|\n")
	sb.WriteString(fmt.Sprintf("| result | %s | %s |\n", doc.Returns.Type, doc.Returns.Description))

	return sb.String()
}

// GenerateExtensionPointMarkdown 生成扩展点Markdown文档
func (g *ExtensionDocGenerator) GenerateExtensionPointMarkdown(ctx context.Context, doc *model.ExtensionPointDocMetadata) string {
	var sb strings.Builder

	sb.WriteString(fmt.Sprintf("# Extension Point: %s\n\n", doc.Name))
	sb.WriteString(fmt.Sprintf("## Description\n\n%s\n\n", doc.Description))

	if len(doc.Extensions) > 0 {
		sb.WriteString("## Extensions\n\n")
		sb.WriteString("| Name | Priority | Description |\n")
		sb.WriteString("|------|----------|-------------|\n")
		for _, ext := range doc.Extensions {
			sb.WriteString(fmt.Sprintf("| %s | %d | %s |\n", ext.Name, ext.Priority, ext.Description))
		}
		sb.WriteString("\n")
	}

	return sb.String()
}
