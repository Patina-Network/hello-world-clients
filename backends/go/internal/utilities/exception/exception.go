package exception

import (
	"encoding/json"
	"log/slog"
	"net/http"

	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
	"google.golang.org/protobuf/encoding/protojson"
	"google.golang.org/protobuf/proto"
)

func Respond(w http.ResponseWriter, msg proto.Message, err error) {
	if err != nil {
		code := status.Code(err)
		slog.Warn("gRPC request failed", "code", code.String())
		httpCode, message := grpcError(code)
		Fail(w, httpCode, message)
		return
	}
	data, err := (protojson.MarshalOptions{EmitUnpopulated: true}).Marshal(msg)
	if err != nil {
		Fail(w, http.StatusInternalServerError, "response encoding failed")
		return
	}
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	_, _ = w.Write(data)
}

func Fail(w http.ResponseWriter, code int, message string) {
	writeJSON(w, code, map[string]string{"error": message})
}

func grpcError(code codes.Code) (int, string) {
	switch code {
	case codes.InvalidArgument:
		return http.StatusBadRequest, "invalid request"
	case codes.NotFound:
		return http.StatusNotFound, "not found"
	case codes.AlreadyExists:
		return http.StatusConflict, "already exists"
	case codes.Unauthenticated:
		return http.StatusUnauthorized, "authentication required"
	case codes.PermissionDenied:
		return http.StatusForbidden, "permission denied"
	case codes.ResourceExhausted:
		return http.StatusTooManyRequests, "resource exhausted"
	case codes.Unavailable:
		return http.StatusServiceUnavailable, "service unavailable"
	case codes.DeadlineExceeded, codes.Canceled:
		return http.StatusGatewayTimeout, "upstream timeout"
	default:
		return http.StatusBadGateway, "upstream request failed"
	}
}

func writeJSON(w http.ResponseWriter, code int, value any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(code)
	_ = json.NewEncoder(w).Encode(value)
}
