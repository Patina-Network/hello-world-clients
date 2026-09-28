use tower_http::services::ServeDir;

pub fn static_files(static_dir: &str) -> ServeDir {
    ServeDir::new(static_dir)
}
