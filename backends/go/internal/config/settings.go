package config

import (
	"context"
	"fmt"
	"github.com/urfave/cli/v3"
	"time"
)

type Config struct {
	Target    string
	TLS       bool
	Timeout   time.Duration
	HTTPAddr  string
	StaticDir string
}

func Command(run func(Config) error) *cli.Command {
	return &cli.Command{
		Name: "hello-world-client-go",
		Flags: []cli.Flag{
			&cli.StringFlag{Name: "grpc-target", Value: "hello-world-grpc-service:50051", Sources: cli.EnvVars("GRPC_TARGET")},
			&cli.BoolFlag{Name: "grpc-tls", Sources: cli.EnvVars("GRPC_TLS")},
			&cli.IntFlag{Name: "grpc-timeout-ms", Value: 3000, Sources: cli.EnvVars("GRPC_TIMEOUT_MS")},
			&cli.StringFlag{Name: "http-addr", Value: ":8080", Sources: cli.EnvVars("HTTP_ADDR")},
			&cli.StringFlag{Name: "static-dir", Value: "../../frontend/dist", Sources: cli.EnvVars("STATIC_DIR")},
		},
		Action: func(_ context.Context, cmd *cli.Command) error {
			ms := cmd.Int("grpc-timeout-ms")
			if ms < 1 || ms > 30000 {
				return fmt.Errorf("GRPC_TIMEOUT_MS must be 1–30000")
			}
			return run(Config{
				Target:    cmd.String("grpc-target"),
				TLS:       cmd.Bool("grpc-tls"),
				Timeout:   time.Duration(ms) * time.Millisecond,
				HTTPAddr:  cmd.String("http-addr"),
				StaticDir: cmd.String("static-dir"),
			})
		},
	}
}
