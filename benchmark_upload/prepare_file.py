import argparse
from pathlib import Path


def format_size(size_bytes):
    mib = size_bytes / 1024 / 1024
    gib = mib / 1024
    return mib, gib


def prepare_file(output, size_mb):
    output_path = Path(output)
    size_bytes = size_mb * 1024 * 1024

    if output_path.exists() and output_path.stat().st_size == size_bytes:
        return output_path, size_bytes, False

    output_path.parent.mkdir(parents=True, exist_ok=True)
    chunk = b"x" * (1024 * 1024)
    remaining = size_bytes

    with output_path.open("wb") as file:
        while remaining > 0:
            write_size = min(len(chunk), remaining)
            file.write(chunk[:write_size])
            remaining -= write_size

    return output_path, size_bytes, True


def main():
    parser = argparse.ArgumentParser(description="Prepare a fixed-size upload benchmark file.")
    parser.add_argument("--size-mb", type=int, required=True, help="Target file size in MiB.")
    parser.add_argument("--output", required=True, help="Output file path.")
    args = parser.parse_args()

    if args.size_mb <= 0:
        raise SystemExit("--size-mb must be greater than 0")

    path, size_bytes, created = prepare_file(args.output, args.size_mb)
    mib, gib = format_size(size_bytes)
    action = "created" if created else "reused"

    print(f"file={path.resolve()}")
    print(f"action={action}")
    print(f"bytes={size_bytes}")
    print(f"MiB={mib:.2f}")
    print(f"GiB={gib:.3f}")


if __name__ == "__main__":
    main()
