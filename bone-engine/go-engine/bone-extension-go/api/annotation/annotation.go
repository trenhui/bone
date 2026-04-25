package annotation

type ExtensionPoint struct {
	Name        string
	Description string
}

type Extension struct {
	Point   string
	Name    string
	Priority int
}

type Interceptor struct {
	Pattern string
}

type Filter struct {
	Pattern string
	Order   int
}

type Listener struct {
	Event string
}

type Service struct {
	Name string
}

type Component struct {
	Name string
}

type Autowired struct {
	Name string
}

type Value struct {
	Key string
}

type Config struct {
	Prefix string
}
