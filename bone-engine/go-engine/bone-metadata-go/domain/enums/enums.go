package enums

type Operator string

const (
	EQ         Operator = "="
	NE         Operator = "!="
	GT         Operator = ">"
	GE         Operator = ">="
	LT         Operator = "<"
	LE         Operator = "<="
	LIKE       Operator = "LIKE"
	NOTLIKE    Operator = "NOT LIKE"
	IN         Operator = "IN"
	NOTIN      Operator = "NOT IN"
	ISNULL     Operator = "IS NULL"
	ISNOTNULL  Operator = "IS NOT NULL"
	BETWEEN    Operator = "BETWEEN"
	NOTBETWEEN Operator = "NOT BETWEEN"
)

type JoinType string

const (
	INNER JoinType = "INNER"
	LEFT  JoinType = "LEFT"
	RIGHT JoinType = "RIGHT"
	FULL  JoinType = "FULL"
)

type OrderDirection string

const (
	ASC  OrderDirection = "ASC"
	DESC OrderDirection = "DESC"
)

type DialectType string

const (
	MySQL    DialectType = "mysql"
	Postgres DialectType = "postgres"
	SQLite   DialectType = "sqlite"
)

type LogicType string

const (
	AND LogicType = "AND"
	OR  LogicType = "OR"
)

type AggregateFunction string

const (
	COUNT AggregateFunction = "COUNT"
	SUM   AggregateFunction = "SUM"
	AVG   AggregateFunction = "AVG"
	MAX   AggregateFunction = "MAX"
	MIN   AggregateFunction = "MIN"
)

type TransactionIsolation string

const (
	LevelDefault         TransactionIsolation = "DEFAULT"
	LevelReadUncommitted TransactionIsolation = "READ UNCOMMITTED"
	LevelReadCommitted   TransactionIsolation = "READ COMMITTED"
	LevelRepeatableRead  TransactionIsolation = "REPEATABLE READ"
	LevelSerializable    TransactionIsolation = "SERIALIZABLE"
)
