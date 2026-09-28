package main

import (
	"context"
	"crypto/tls"
	"errors"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"strconv"
	"syscall"
	"time"

	pb "github.com/Patina-Network/hello-world-clients/backends/go/gen/helloworld"
	"github.com/Patina-Network/hello-world-clients/backends/go/internal/api"
	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials"
	"google.golang.org/grpc/credentials/insecure"
)

func env(key, fallback string) string {
	if s := os.Getenv(key); s != "" {
		return s
	}
	return fallback
}
func run() error {
	timeoutMS, err := strconv.ParseInt(env("GRPC_TIMEOUT_MS", "3000"), 10, 64)
	if err != nil {
		return err
	}
	if timeoutMS <= 0 || timeoutMS > 30000 {
		return errors.New("GRPC_TIMEOUT_MS must be 1–30000")
	}
	timeout := time.Duration(timeoutMS) * time.Millisecond
	var creds credentials.TransportCredentials
	switch env("GRPC_TLS", "false") {
	case "true":
		creds = credentials.NewTLS(&tls.Config{MinVersion: tls.VersionTLS12})
	case "false":
		creds = insecure.NewCredentials()
	default:
		return errors.New("GRPC_TLS must be true or false")
	}
	conn, err := grpc.NewClient(env("GRPC_TARGET", "hello-world-grpc-service:50051"), grpc.WithTransportCredentials(creds))
	if err != nil {
		return err
	}
	defer conn.Close()
	server := &http.Server{Addr: env("HTTP_ADDR", ":8080"), Handler: (api.API{Client: pb.NewGreeterServiceClient(conn), Timeout: timeout}).Handler(env("STATIC_DIR", "../../frontend/dist")), ReadHeaderTimeout: 5 * time.Second, ReadTimeout: 10 * time.Second, WriteTimeout: 35 * time.Second, IdleTimeout: 60 * time.Second, MaxHeaderBytes: 16 << 10}
	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()
	done := make(chan error, 1)
	go func() { done <- server.ListenAndServe() }()
	slog.Info("HTTP server started", "address", server.Addr)
	select {
	case err := <-done:
		if !errors.Is(err, http.ErrServerClosed) {
			return err
		}
	case <-ctx.Done():
	}
	shutdown, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	return server.Shutdown(shutdown)
}
func main() {
	slog.SetDefault(slog.New(slog.NewJSONHandler(os.Stdout, nil)))
	if err := run(); err != nil {
		slog.Error("server stopped", "error", err)
		os.Exit(1)
	}
}
