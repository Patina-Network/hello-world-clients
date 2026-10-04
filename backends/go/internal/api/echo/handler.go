package echo

import (
	"context"
	"net/http"
	"time"

	"github.com/Patina-Network/hello-world-clients/backends/go/internal/httpresponse"
	"github.com/Patina-Network/hello-world-clients/backends/go/internal/validate"
	pb "patinanetwork.org/grpc/hello-world-grpc-service"
)

func Echo(client pb.GreeterServiceClient, timeout time.Duration) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		name := r.URL.Query().Get("name")
		if !validate.Text(name, 256) {
			httpresponse.Fail(w, http.StatusBadRequest, "name must contain 1–256 UTF-8 bytes")
			return
		}
		ctx, cancel := context.WithTimeout(r.Context(), timeout)
		defer cancel()
		reply, err := client.EchoHello(ctx, &pb.EchoHelloRequest{Name: name})
		httpresponse.Respond(w, reply, err)
	}
}
