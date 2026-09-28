package echo

import (
	"context"
	"net/http"
	"time"

	pb "github.com/Patina-Network/hello-world-clients/backends/go/gen/helloworld"
	"github.com/Patina-Network/hello-world-clients/backends/go/internal/common/validate"
	"github.com/Patina-Network/hello-world-clients/backends/go/internal/utilities/exception"
)

func Echo(client pb.GreeterServiceClient, timeout time.Duration) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		name := r.URL.Query().Get("name")
		if !validate.Text(name, 256) {
			exception.Fail(w, http.StatusBadRequest, "name must contain 1–256 UTF-8 bytes")
			return
		}
		ctx, cancel := context.WithTimeout(r.Context(), timeout)
		defer cancel()
		reply, err := client.EchoHello(ctx, &pb.EchoHelloRequest{Name: name})
		exception.Respond(w, reply, err)
	}
}
