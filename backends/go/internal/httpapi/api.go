package httpapi

import (
	"context"
	"encoding/json"
	"io"
	"log/slog"
	"net/http"
	"strings"
	"time"

	pb "github.com/Patina-Network/hello-world-clients/backends/go/gen/helloworld"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
	"google.golang.org/protobuf/encoding/protojson"
	"google.golang.org/protobuf/proto"
)

const MaxBody = 16 << 10

type API struct {
	Client  pb.GreeterServiceClient
	Timeout time.Duration
}

func (a API) Handler(staticDir string) http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /healthz", func(w http.ResponseWriter, r *http.Request) { writeJSON(w, 200, map[string]string{"status": "ok"}) })
	mux.HandleFunc("GET /api/echo", a.echo)
	mux.HandleFunc("POST /api/greetings", a.send)
	mux.HandleFunc("GET /api/greetings", a.list)
	mux.HandleFunc("/api/", func(w http.ResponseWriter, r *http.Request) { fail(w, 404, "not found") })
	mux.Handle("/", http.FileServer(http.Dir(staticDir)))
	return mux
}
func valid(s string, max int) bool { return strings.TrimSpace(s) != "" && len(s) <= max }
func (a API) echo(w http.ResponseWriter, r *http.Request) {
	name := r.URL.Query().Get("name")
	if !valid(name, 256) {
		fail(w, 400, "name must contain 1–256 UTF-8 bytes")
		return
	}
	ctx, cancel := context.WithTimeout(r.Context(), a.Timeout)
	defer cancel()
	reply, err := a.Client.EchoHello(ctx, &pb.EchoHelloRequest{Name: name})
	respond(w, reply, err)
}
func (a API) send(w http.ResponseWriter, r *http.Request) {
	if strings.Split(r.Header.Get("Content-Type"), ";")[0] != "application/json" {
		fail(w, 415, "application/json required")
		return
	}
	body, err := io.ReadAll(http.MaxBytesReader(w, r.Body, MaxBody))
	if err != nil {
		fail(w, 413, "request body too large")
		return
	}
	var input struct {
		SenderName    string `json:"senderName"`
		RecipientName string `json:"recipientName"`
		Greeting      string `json:"greeting"`
	}
	dec := json.NewDecoder(strings.NewReader(string(body)))
	dec.DisallowUnknownFields()
	if err = dec.Decode(&input); err != nil {
		fail(w, 400, "invalid JSON request")
		return
	}
	if dec.Decode(new(any)) != io.EOF {
		fail(w, 400, "invalid JSON request")
		return
	}
	if !valid(input.SenderName, 256) || !valid(input.RecipientName, 256) || !valid(input.Greeting, 4096) {
		fail(w, 400, "senderName, recipientName and greeting are required (256/256/4096 byte limits)")
		return
	}
	ctx, cancel := context.WithTimeout(r.Context(), a.Timeout)
	defer cancel()
	reply, err := a.Client.SayGreeting(ctx, &pb.SayGreetingRequest{SenderName: input.SenderName, RecipientName: input.RecipientName, Greeting: input.Greeting})
	respond(w, reply, err)
}
func (a API) list(w http.ResponseWriter, r *http.Request) {
	var name *string
	if values, ok := r.URL.Query()["recipientName"]; ok {
		n := values[0]
		if len(n) > 256 {
			fail(w, 400, "recipientName exceeds 256 UTF-8 bytes")
			return
		}
		name = &n
	}
	ctx, cancel := context.WithTimeout(r.Context(), a.Timeout)
	defer cancel()
	reply, err := a.Client.GetGreetingsByName(ctx, &pb.GetGreetingsByNameRequest{RecipientName: name})
	respond(w, reply, err)
}
func respond(w http.ResponseWriter, msg proto.Message, err error) {
	if err != nil {
		code := status.Code(err)
		httpCode := 502
		message := "upstream request failed"
		switch code {
		case codes.InvalidArgument:
			httpCode = 400
			message = "invalid request"
		case codes.NotFound:
			httpCode = 404
			message = "not found"
		case codes.AlreadyExists:
			httpCode = 409
			message = "already exists"
		case codes.Unauthenticated:
			httpCode = 401
			message = "authentication required"
		case codes.PermissionDenied:
			httpCode = 403
			message = "permission denied"
		case codes.ResourceExhausted:
			httpCode = 429
			message = "resource exhausted"
		case codes.Unavailable:
			httpCode = 503
			message = "service unavailable"
		case codes.DeadlineExceeded, codes.Canceled:
			httpCode = 504
			message = "upstream timeout"
		}
		slog.Warn("gRPC request failed", "code", code.String())
		fail(w, httpCode, message)
		return
	}
	data, err := (protojson.MarshalOptions{EmitUnpopulated: true}).Marshal(msg)
	if err != nil {
		fail(w, 500, "response encoding failed")
		return
	}
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(200)
	_, _ = w.Write(data)
}
func fail(w http.ResponseWriter, code int, message string) {
	writeJSON(w, code, map[string]string{"error": message})
}
func writeJSON(w http.ResponseWriter, code int, value any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(code)
	_ = json.NewEncoder(w).Encode(value)
}
