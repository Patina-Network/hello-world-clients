use serde::Deserialize;

#[derive(Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct SayGreetingBody {
    pub sender_name: String,
    pub recipient_name: String,
    pub greeting: String,
}
