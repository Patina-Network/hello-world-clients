package validate

import "strings"

func Text(value string, maxBytes int) bool {
	return strings.TrimSpace(value) != "" && len(value) <= maxBytes
}
