package api

import (
	"net/http"
	"time"

	"github.com/Patina-Network/hello-world-clients/backends/go/internal/api/echo"
	"github.com/Patina-Network/hello-world-clients/backends/go/internal/api/greetings"
	"github.com/Patina-Network/hello-world-clients/backends/go/internal/api/health"
	"github.com/Patina-Network/hello-world-clients/backends/go/internal/httpresponse"
	"github.com/Patina-Network/hello-world-clients/backends/go/internal/staticcontent"
	pb "patinanetwork.org/grpc/hello-world-grpc-service"
)

type API struct {
	Client  pb.GreeterServiceClient
	Timeout time.Duration
}

func (a API) Handler(staticDir string) http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /healthz", health.Handler())
	mux.HandleFunc("GET /api/echo", echo.Echo(a.Client, a.Timeout))
	mux.HandleFunc("POST /api/greetings", greetings.Send(a.Client, a.Timeout))
	mux.HandleFunc("GET /api/greetings", greetings.List(a.Client, a.Timeout))
	mux.HandleFunc("/api/", func(w http.ResponseWriter, r *http.Request) {
		httpresponse.Fail(w, http.StatusNotFound, "not found")
	})
	mux.Handle("/", staticcontent.Handler(staticDir))
	return mux
}
