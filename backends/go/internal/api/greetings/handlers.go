package greetings

import (
	"context"
	"encoding/json"
	"io"
	"net/http"
	"strings"
	"time"

	"github.com/Patina-Network/hello-world-clients/backends/go/internal/api/greetings/body"
	"github.com/Patina-Network/hello-world-clients/backends/go/internal/httpresponse"
	"github.com/Patina-Network/hello-world-clients/backends/go/internal/validate"
	pb "patinanetwork.org/grpc/hello-world-grpc-service"
)

const MaxBody = 16 << 10

func Send(client pb.GreeterServiceClient, timeout time.Duration) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if strings.Split(r.Header.Get("Content-Type"), ";")[0] != "application/json" {
			httpresponse.Fail(w, http.StatusUnsupportedMediaType, "application/json required")
			return
		}
		payload, err := io.ReadAll(http.MaxBytesReader(w, r.Body, MaxBody))
		if err != nil {
			httpresponse.Fail(w, http.StatusRequestEntityTooLarge, "request body too large")
			return
		}
		var input body.SayGreeting
		dec := json.NewDecoder(strings.NewReader(string(payload)))
		dec.DisallowUnknownFields()
		if err = dec.Decode(&input); err != nil {
			httpresponse.Fail(w, http.StatusBadRequest, "invalid JSON request")
			return
		}
		if dec.Decode(new(any)) != io.EOF {
			httpresponse.Fail(w, http.StatusBadRequest, "invalid JSON request")
			return
		}
		if !validate.Text(input.SenderName, 256) || !validate.Text(input.RecipientName, 256) || !validate.Text(input.Greeting, 4096) {
			httpresponse.Fail(w, http.StatusBadRequest, "senderName, recipientName and greeting are required (256/256/4096 byte limits)")
			return
		}
		ctx, cancel := context.WithTimeout(r.Context(), timeout)
		defer cancel()
		reply, err := client.SayGreeting(ctx, &pb.SayGreetingRequest{
			SenderName:    input.SenderName,
			RecipientName: input.RecipientName,
			Greeting:      input.Greeting,
		})
		httpresponse.Respond(w, reply, err)
	}
}

func List(client pb.GreeterServiceClient, timeout time.Duration) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		var name *string
		if values, ok := r.URL.Query()["recipientName"]; ok {
			n := values[0]
			if len(n) > 256 {
				httpresponse.Fail(w, http.StatusBadRequest, "recipientName exceeds 256 UTF-8 bytes")
				return
			}
			name = &n
		}
		ctx, cancel := context.WithTimeout(r.Context(), timeout)
		defer cancel()
		reply, err := client.GetGreetingsByName(ctx, &pb.GetGreetingsByNameRequest{RecipientName: name})
		httpresponse.Respond(w, reply, err)
	}
}
