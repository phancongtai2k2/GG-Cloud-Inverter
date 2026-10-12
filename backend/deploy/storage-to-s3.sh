#!/bin/sh
# Plan B6: copies the uploaded files kept on disk (./data/storage) into the S3 bucket, keeping the same layout
# (<area>/<name>), so the API can switch to Storage__Provider=s3 without losing pictures. Safe to run again: only
# missing or changed files are copied. Run from this folder after `docker compose --profile multi up -d files`.
#   ./storage-to-s3.sh            (reads S3_ACCESS_KEY, S3_SECRET_KEY, S3_BUCKET from .env)
set -eu
cd "$(dirname "$0")"
set -a; . ./.env; set +a
: "${S3_SECRET_KEY:?set in .env}"
bucket="${S3_BUCKET:-ggpower}"
run() {
  docker run --rm --network ggpower_internal -v "$PWD/data/storage:/storage:ro" \
    -e AWS_ACCESS_KEY_ID="${S3_ACCESS_KEY:-ggpower}" -e AWS_SECRET_ACCESS_KEY="$S3_SECRET_KEY" -e AWS_DEFAULT_REGION=us-east-1 \
    -e AWS_REQUEST_CHECKSUM_CALCULATION=when_required -e AWS_RESPONSE_CHECKSUM_VALIDATION=when_required \
    amazon/aws-cli:2.17.0 --endpoint-url http://files:8333 "$@"
}
run s3 mb "s3://$bucket" 2>/dev/null || true
run s3 sync /storage "s3://$bucket"
echo "Copied. Files on disk: $(find data/storage -type f | wc -l); in the bucket: $(run s3 ls "s3://$bucket" --recursive | wc -l)"
