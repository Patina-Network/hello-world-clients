package body

type SayGreeting struct {
	SenderName    string `json:"senderName"`
	RecipientName string `json:"recipientName"`
	Greeting      string `json:"greeting"`
}
