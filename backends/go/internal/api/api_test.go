package api

import (
	"context"
	"encoding/json"
	"net"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync"
	"testing"
	"time"

	pb "github.com/Patina-Network/hello-world-clients/backends/go/gen/helloworld"
	"github.com/Patina-Network/hello-world-clients/backends/go/internal/api/greetings"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/credentials/insecure"
	"google.golang.org/grpc/status"
	"google.golang.org/grpc/test/bufconn"
	"google.golang.org/protobuf/types/known/timestamppb"
)

type fakeGreeter struct {
	pb.UnimplementedGreeterServiceServer
	mu     sync.Mutex
	sent   *pb.SayGreetingRequest
	filter *pb.GetGreetingsByNameRequest
}

func (f *fakeGreeter) EchoHello(ctx context.Context, r *pb.EchoHelloRequest) (*pb.EchoHelloResponse, error) {
	switch r.Name {
	case "unavailable":
		return nil, status.Error(codes.Unavailable, "private infrastructure details")
	case "invalid":
		return nil, status.Error(codes.InvalidArgument, "bad name")
	case "slow":
		<-ctx.Done()
		return nil, status.FromContextError(ctx.Err()).Err()
	}
	return &pb.EchoHelloResponse{Response: "hello " + r.Name}, nil
}
func (f *fakeGreeter) SayGreeting(_ context.Context, r *pb.SayGreetingRequest) (*pb.SayGreetingResponse, error) {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.sent = r
	return &pb.SayGreetingResponse{}, nil
}
func (f *fakeGreeter) GetGreetingsByName(_ context.Context, r *pb.GetGreetingsByNameRequest) (*pb.GreetingsResponse, error) {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.filter = r
	if r.GetRecipientName() == "missing" {
		return nil, status.Error(codes.NotFound, "missing")
	}
	return &pb.GreetingsResponse{Replies: []*pb.GreetingResponse{{Id: 4294967295, Message: "hi", SenderName: "Ada", RecipientName: "Lin", ReceivedAt: timestamppb.New(time.Unix(0, 0))}}}, nil
}
func setup(t *testing.T) (http.Handler, *fakeGreeter) {
	t.Helper()
	listener := bufconn.Listen(1 << 20)
	server := grpc.NewServer()
	fake := &fakeGreeter{}
	pb.RegisterGreeterServiceServer(server, fake)
	go func() { _ = server.Serve(listener) }()
	t.Cleanup(server.Stop)
	conn, err := grpc.NewClient("passthrough:///mock", grpc.WithTransportCredentials(insecure.NewCredentials()), grpc.WithContextDialer(func(context.Context, string) (net.Conn, error) { return listener.Dial() }))
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = conn.Close() })
	return (API{Client: pb.NewGreeterServiceClient(conn), Timeout: 100 * time.Millisecond}).Handler(t.TempDir()), fake
}
func call(handler http.Handler, method, path, body string) *httptest.ResponseRecorder {
	r := httptest.NewRequest(method, path, strings.NewReader(body))
	if body != "" {
		r.Header.Set("Content-Type", "application/json")
	}
	w := httptest.NewRecorder()
	handler.ServeHTTP(w, r)
	return w
}
func TestHTTPThroughGeneratedClient(t *testing.T) {
	handler, fake := setup(t)
	t.Run("echo", func(t *testing.T) {
		w := call(handler, "GET", "/api/echo?name=Ada", "")
		if w.Code != 200 || !strings.Contains(w.Body.String(), "hello Ada") {
			t.Fatalf("%d %s", w.Code, w.Body)
		}
	})
	t.Run("send", func(t *testing.T) {
		w := call(handler, "POST", "/api/greetings", `{"senderName":"Ada","recipientName":"Lin","greeting":"hi"}`)
		if w.Code != 200 {
			t.Fatalf("%d %s", w.Code, w.Body)
		}
		fake.mu.Lock()
		defer fake.mu.Unlock()
		if fake.sent.GetSenderName() != "Ada" || fake.sent.GetRecipientName() != "Lin" || fake.sent.GetGreeting() != "hi" {
			t.Fatal(fake.sent)
		}
	})
	for _, path := range []string{"/api/greetings", "/api/greetings?recipientName=", "/api/greetings?recipientName=Lin"} {
		t.Run(path, func(t *testing.T) {
			w := call(handler, "GET", path, "")
			if w.Code != 200 {
				t.Fatalf("%d %s", w.Code, w.Body)
			}
			var body struct {
				Replies []struct {
					ID         uint32 `json:"id"`
					ReceivedAt string `json:"receivedAt"`
				}
			}
			if err := json.Unmarshal(w.Body.Bytes(), &body); err != nil {
				t.Fatal(err)
			}
			if len(body.Replies) != 1 || body.Replies[0].ID != 4294967295 || body.Replies[0].ReceivedAt != "1970-01-01T00:00:00Z" {
				t.Fatal(w.Body)
			}
			fake.mu.Lock()
			defer fake.mu.Unlock()
			if (fake.filter.RecipientName != nil) != strings.Contains(path, "?") {
				t.Fatal("optional field presence lost")
			}
		})
	}
}
func TestErrorsAndValidation(t *testing.T) {
	handler, _ := setup(t)
	cases := []struct {
		method, path, body string
		code               int
	}{
		{"GET", "/api/echo", "", 400}, {"GET", "/api/echo?name=%20", "", 400},
		{"GET", "/api/echo?name=" + strings.Repeat("x", 257), "", 400},
		{"GET", "/api/echo?name=unavailable", "", 503}, {"GET", "/api/echo?name=invalid", "", 400}, {"GET", "/api/echo?name=slow", "", 504},
		{"GET", "/api/greetings?recipientName=missing", "", 404},
		{"POST", "/api/greetings", `{`, 400}, {"POST", "/api/greetings", `{}`, 400},
		{"POST", "/api/greetings", `{"senderName":"A","recipientName":"B","greeting":"hi","unknown":1}`, 400},
		{"POST", "/api/greetings", strings.Repeat("x", greetings.MaxBody+1), 413},
		{"POST", "/api/greetings", `{"senderName":"A","recipientName":"B","greeting":"hi"} {}`, 400},
		{"GET", "/healthz", "", 200},
	}
	for _, tt := range cases {
		t.Run(tt.path+tt.body[:min(8, len(tt.body))], func(t *testing.T) {
			w := call(handler, tt.method, tt.path, tt.body)
			if w.Code != tt.code {
				t.Fatalf("want %d got %d: %s", tt.code, w.Code, w.Body)
			}
			if strings.Contains(w.Body.String(), "private infrastructure") {
				t.Fatal("leaked upstream error")
			}
		})
	}
}
func TestRequestCancellation(t *testing.T) {
	handler, _ := setup(t)
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	r := httptest.NewRequest("GET", "/api/echo?name=slow", nil).WithContext(ctx)
	w := httptest.NewRecorder()
	handler.ServeHTTP(w, r)
	if w.Code != 504 {
		t.Fatalf("got %d", w.Code)
	}
}
