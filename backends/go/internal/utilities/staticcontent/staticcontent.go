package staticcontent

import "net/http"

func Handler(dir string) http.Handler {
	return http.FileServer(http.Dir(dir))
}
