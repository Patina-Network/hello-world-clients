package main

import (
	"context"
	"crypto/tls"
	"errors"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/Patina-Network/hello-world-clients/backends/go/internal/api"
	"github.com/Patina-Network/hello-world-clients/backends/go/internal/config"
	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials"
	"google.golang.org/grpc/credentials/insecure"
	pb "patinanetwork.org/grpc/hello-world-grpc-service"
)

func run(cfg config.Config) error {
	var creds credentials.TransportCredentials
	switch cfg.TLS {
	case true:
		creds = credentials.NewTLS(&tls.Config{MinVersion: tls.VersionTLS12})
	case false:
		creds = insecure.NewCredentials()
	}
	conn, err := grpc.NewClient(cfg.Target, grpc.WithTransportCredentials(creds))
	if err != nil {
		return err
	}
	defer conn.Close()
	server := &http.Server{
		Addr: cfg.HTTPAddr,
		Handler: (api.API{
			Client:  pb.NewGreeterServiceClient(conn),
			Timeout: cfg.Timeout,
		}).Handler(cfg.StaticDir),
		ReadHeaderTimeout: 5 * time.Second,
		ReadTimeout:       10 * time.Second,
		WriteTimeout:      35 * time.Second,
		IdleTimeout:       60 * time.Second,
		MaxHeaderBytes:    16 << 10,
	}
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
	if err := config.Command(run).Run(context.Background(), os.Args); err != nil {
		slog.Error("server stopped", "error", err)
		os.Exit(1)
	}
}
