fn main() -> Result<(), Box<dyn std::error::Error>> {
    let mut config = tonic_prost_build::Config::new();
    config.protoc_executable(protoc_bin_vendored::protoc_bin_path()?);
    tonic_prost_build::configure().compile_with_config(
        config,
        &[std::path::PathBuf::from("../../proto/v1/helloworld.proto")],
        &[
            std::path::PathBuf::from("../../proto"),
            protoc_bin_vendored::include_path()?,
        ],
    )?;
    println!("cargo:rerun-if-changed=../../proto/v1/helloworld.proto");
    Ok(())
}
