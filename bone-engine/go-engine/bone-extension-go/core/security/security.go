package security

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"os"
	"path/filepath"
	"plugin"
	"strings"

	"github.com/bone-engine/bone-extension-go/api/exception"
	"github.com/bone-engine/bone-extension-go/api/model"
	"github.com/bone-engine/bone-extension-go/api/spi"
)

// Permission 权限定义
type Permission struct {
	Action    string
	Resource  string
	Condition string
}

// ExtensionPermissionManager 扩展权限管理器
type ExtensionPermissionManager struct {
	permissions map[string][]Permission
}

// NewExtensionPermissionManager 创建权限管理器
func NewExtensionPermissionManager() *ExtensionPermissionManager {
	return &ExtensionPermissionManager{
		permissions: make(map[string][]Permission),
	}
}

// RegisterPermission 注册权限
func (pm *ExtensionPermissionManager) RegisterPermission(extensionName string, perm Permission) {
	pm.permissions[extensionName] = append(pm.permissions[extensionName], perm)
}

// CheckPermission 检查权限
func (pm *ExtensionPermissionManager) CheckPermission(extension spi.Extension, ctx *model.Context) (bool, error) {
	extName := extension.Name()
	perms, exists := pm.permissions[extName]
	if !exists {
		return true, nil // 没有注册权限的扩展默认允许
	}

	for _, perm := range perms {
		if !pm.checkPermission(perm, ctx) {
			return false, exception.NewPermissionDeniedError(extName, perm.Action, perm.Resource)
		}
	}

	return true, nil
}

// checkPermission 检查单个权限
func (pm *ExtensionPermissionManager) checkPermission(perm Permission, ctx *model.Context) bool {
	// 这里实现权限检查逻辑
	// 可以从上下文获取用户信息、资源信息等
	return true // 简化实现
}

// ExtensionSandbox 扩展沙箱
type ExtensionSandbox struct {
	allowedImports []string
	maxMemory     int64
	maxCPU        int
	allowedFiles  []string
}

// NewExtensionSandbox 创建沙箱
func NewExtensionSandbox() *ExtensionSandbox {
	return &ExtensionSandbox{
		allowedImports: []string{
			"fmt",
			"strings",
			"context",
			"time",
		},
		maxMemory:    100 * 1024 * 1024, // 100MB
		maxCPU:       10,                // 10% CPU
		allowedFiles: []string{},
	}
}

// ExecuteInSandbox 在沙箱中执行扩展
func (sb *ExtensionSandbox) ExecuteInSandbox(ext spi.Extension, ctx *model.Context) (*model.Result, error) {
	// 这里实现沙箱执行逻辑
	// 可以使用 plugin 包加载扩展，限制资源使用等
	
	// 简化实现：直接执行
	return ext.Execute(ctx)
}

// LoadExtensionPlugin 加载扩展插件
func (sb *ExtensionSandbox) LoadExtensionPlugin(pluginPath string) (spi.Extension, error) {
	if !sb.isAllowedPath(pluginPath) {
		return nil, fmt.Errorf("plugin path not allowed: %s", pluginPath)
	}

	p, err := plugin.Open(pluginPath)
	if err != nil {
		return nil, err
	}

	sym, err := p.Lookup("NewExtension")
	if err != nil {
		return nil, err
	}

	newExt, ok := sym.(func() spi.Extension)
	if !ok {
		return nil, fmt.Errorf("invalid plugin symbol")
	}

	return newExt(), nil
}

// isAllowedPath 检查路径是否允许
func (sb *ExtensionSandbox) isAllowedPath(path string) bool {
	absPath, err := filepath.Abs(path)
	if err != nil {
		return false
	}

	for _, allowed := range sb.allowedFiles {
		allowedAbs, err := filepath.Abs(allowed)
		if err != nil {
			continue
		}
		if strings.HasPrefix(absPath, allowedAbs) {
			return true
		}
	}

	return false
}

// ExtensionSignatureVerifier 扩展签名验证器
type ExtensionSignatureVerifier struct {
	publicKey string
}

// NewExtensionSignatureVerifier 创建签名验证器
func NewExtensionSignatureVerifier(publicKey string) *ExtensionSignatureVerifier {
	return &ExtensionSignatureVerifier{
		publicKey: publicKey,
	}
}

// VerifySignature 验证签名
func (sv *ExtensionSignatureVerifier) VerifySignature(extensionName string, code []byte, signature string) (bool, error) {
	// 这里实现签名验证逻辑
	// 可以使用公钥验证签名
	
	// 简化实现：使用 SHA256 哈希验证
	hash := sha256.Sum256(code)
	expectedSignature := hex.EncodeToString(hash[:])
	
	return expectedSignature == signature, nil
}

// VerifyExtension 验证扩展
func (sv *ExtensionSignatureVerifier) VerifyExtension(extension spi.Extension, codePath string) (bool, error) {
	code, err := os.ReadFile(codePath)
	if err != nil {
		return false, err
	}

	// 从扩展元数据中获取签名
	signature := "" // 简化实现
	
	return sv.VerifySignature(extension.Name(), code, signature)
}

// SecurityManager 安全管理器
type SecurityManager struct {
	permissionManager  *ExtensionPermissionManager
	sandbox            *ExtensionSandbox
	signatureVerifier  *ExtensionSignatureVerifier
}

// NewSecurityManager 创建安全管理器
func NewSecurityManager(publicKey string) *SecurityManager {
	return &SecurityManager{
		permissionManager:  NewExtensionPermissionManager(),
		sandbox:            NewExtensionSandbox(),
		signatureVerifier:  NewExtensionSignatureVerifier(publicKey),
	}
}

// CheckPermission 检查权限
func (sm *SecurityManager) CheckPermission(extension spi.Extension, ctx *model.Context) (bool, error) {
	return sm.permissionManager.CheckPermission(extension, ctx)
}

// ExecuteInSandbox 在沙箱中执行
func (sm *SecurityManager) ExecuteInSandbox(extension spi.Extension, ctx *model.Context) (*model.Result, error) {
	// 先检查权限
	hasPermission, err := sm.CheckPermission(extension, ctx)
	if err != nil {
		return nil, err
	}
	if !hasPermission {
		return nil, exception.NewPermissionDeniedError(extension.Name(), "execute", "extension")
	}

	// 在沙箱中执行
	return sm.sandbox.ExecuteInSandbox(extension, ctx)
}

// RegisterPermission 注册权限
func (sm *SecurityManager) RegisterPermission(extensionName string, perm Permission) {
	sm.permissionManager.RegisterPermission(extensionName, perm)
}

// LoadExtension 加载扩展
func (sm *SecurityManager) LoadExtension(pluginPath string) (spi.Extension, error) {
	// 验证签名
	ext, err := sm.sandbox.LoadExtensionPlugin(pluginPath)
	if err != nil {
		return nil, err
	}

	// 验证签名
	_, err = sm.signatureVerifier.VerifyExtension(ext, pluginPath)
	if err != nil {
		return nil, err
	}

	return ext, nil
}
