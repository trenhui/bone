package function

type Predicate func(interface{}) bool
type Function func(interface{}) interface{}
type Consumer func(interface{})
type Supplier func() interface{}
type BiFunction func(interface{}, interface{}) interface{}
type BinaryOperator func(interface{}, interface{}) interface{}
type UnaryOperator func(interface{}) interface{}

func Compose(f, g Function) Function {
	return func(x interface{}) interface{} {
		return f(g(x))
	}
}

func AndThen(f, g Function) Function {
	return func(x interface{}) interface{} {
		return g(f(x))
	}
}

func Identity() Function {
	return func(x interface{}) interface{} {
		return x
	}
}

func And(p1, p2 Predicate) Predicate {
	return func(x interface{}) bool {
		return p1(x) && p2(x)
	}
}

func Or(p1, p2 Predicate) Predicate {
	return func(x interface{}) bool {
		return p1(x) || p2(x)
	}
}

func Not(p Predicate) Predicate {
	return func(x interface{}) bool {
		return !p(x)
	}
}

func IsEqual(target interface{}) Predicate {
	return func(x interface{}) bool {
		return x == target
	}
}

func AlwaysTrue() Predicate {
	return func(x interface{}) bool {
		return true
	}
}

func AlwaysFalse() Predicate {
	return func(x interface{}) bool {
		return false
	}
}

func MinBy(less func(a, b interface{}) bool) BinaryOperator {
	return func(a, b interface{}) interface{} {
		if less(a, b) {
			return a
		}
		return b
	}
}

func MaxBy(less func(a, b interface{}) bool) BinaryOperator {
	return func(a, b interface{}) interface{} {
		if less(a, b) {
			return b
		}
		return a
	}
}
