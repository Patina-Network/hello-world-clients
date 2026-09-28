use axum::{Json, extract::Query, extract::State};
use serde::Deserialize;
use serde_json::{Value, json};

use crate::{
    api::AppState,
    common::{bounded, request, valid},
    pb::EchoHelloRequest,
    utilities::exception::ApiError,
};

#[derive(Deserialize)]
pub struct EchoQuery {
    #[serde(default)]
    name: String,
}

pub async fn echo(
    State(mut state): State<AppState>,
    Query(query): Query<EchoQuery>,
) -> Result<Json<Value>, ApiError> {
    if !valid(&query.name, 256) {
        return Err(ApiError(
            axum::http::StatusCode::BAD_REQUEST,
            "name must contain 1–256 UTF-8 bytes",
        ));
    }
    let out = bounded(
        state.timeout,
        state.client.echo_hello(request(
            EchoHelloRequest { name: query.name },
            state.timeout,
        )),
    )
    .await?;
    Ok(Json(json!({"response": out.response})))
}
