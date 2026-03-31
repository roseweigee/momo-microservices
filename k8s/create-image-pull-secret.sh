#!/bin/bash
# ================================================================
# 建立 ghcr.io imagePullSecret
# 讓 K8s 可以 pull private image
# ================================================================

# 使用方式：
# bash create-image-pull-secret.sh 你的GitHub帳號 你的GitHub_Token

GITHUB_USER=${1:-"YOUR_GITHUB_USERNAME"}
GITHUB_TOKEN=${2:-"YOUR_GITHUB_TOKEN"}

echo "建立 ghcr.io imagePullSecret..."

kubectl create secret docker-registry ghcr-secret \
  --docker-server=ghcr.io \
  --docker-username=${GITHUB_USER} \
  --docker-password=${GITHUB_TOKEN} \
  --namespace=momo-system \
  --dry-run=client -o yaml | kubectl apply -f -

echo "✅ ghcr-secret 建立完成"
echo ""
echo "如果 image 是 public 的，不需要這個 secret。"
echo "設定 image 為 public："
echo "  GitHub → Packages → 你的 image → Package settings → Change visibility → Public"
