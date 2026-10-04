package config

import (
	"context"
	"io"
	"testing"
	"time"
)

func TestEnvironmentAndFlagPrecedence(t *testing.T) {
	t.Setenv("GRPC_TARGET", "upstream:50051")
	t.Setenv("GRPC_TLS", "true")
	t.Setenv("GRPC_TIMEOUT_MS", "500")
	t.Setenv("HTTP_ADDR", ":9090")
	t.Setenv("STATIC_DIR", "/srv/static")
	var got Config
	cmd := Command(func(cfg Config) error { got = cfg; return nil })
	if err := cmd.Run(context.Background(), []string{"server", "--grpc-timeout-ms", "750"}); err != nil {
		t.Fatal(err)
	}
	want := Config{Target: "upstream:50051", TLS: true, Timeout: 750 * time.Millisecond, HTTPAddr: ":9090", StaticDir: "/srv/static"}
	if got != want {
		t.Fatalf("got %+v, want %+v", got, want)
	}
}

func TestInvalidEnvironmentDoesNotStartServer(t *testing.T) {
	for _, tc := range []struct{ key, value string }{
		{"GRPC_TIMEOUT_MS", "0"}, {"GRPC_TIMEOUT_MS", "30001"},
		{"GRPC_TIMEOUT_MS", "oops"}, {"GRPC_TLS", "oops"},
	} {
		t.Run(tc.key+tc.value, func(t *testing.T) {
			t.Setenv(tc.key, tc.value)
			started := false
			cmd := Command(func(Config) error { started = true; return nil })
			cmd.Writer = io.Discard
			cmd.ErrWriter = io.Discard
			if err := cmd.Run(context.Background(), []string{"server"}); err == nil {
				t.Fatal("expected configuration error")
			}
			if started {
				t.Fatal("started with invalid configuration")
			}
		})
	}
}
