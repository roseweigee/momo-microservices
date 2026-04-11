#!/bin/bash
# ================================================================
# ArgoCD 安裝與設定腳本（微服務版）
# 前提：Docker Desktop K8s 已啟動
# ================================================================
set -e

echo "🚀 Step 1: 安裝 ArgoCD"
kubectl create namespace argocd --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml

echo "⏳ 等待 ArgoCD 啟動..."
kubectl wait --for=condition=available deployment/argocd-server -n argocd --timeout=120s

echo ""
echo "🔑 Step 2: 取得初始密碼"
echo "執行："
echo "  kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' | base64 -d"

echo ""
echo "🌐 Step 3: 開啟 ArgoCD UI（另開終端機）"
echo "  kubectl port-forward svc/argocd-server -n argocd 8443:443"
echo "  瀏覽器開啟：https://localhost:8443（帳號 admin）"

echo ""
echo "⚠️  Step 4: 修改 GitHub username"
echo "  把所有 argocd/ 下的 YOUR_GITHUB_USERNAME 換成你的 GitHub 帳號"
echo "  grep -r YOUR_GITHUB_USERNAME argocd/"

echo ""
echo "📋 Step 5: 建立 ArgoCD Project 和 App of Apps"
echo "  kubectl apply -f argocd/app-of-apps.yaml"
echo ""
echo "  ArgoCD 會自動："
echo "  1. 讀取 argocd/apps/ 資料夾"
echo "  2. 建立 user-service、order-service、shipping-service、observability 四個 App"
echo "  3. 監聽 GitHub，有任何 k8s/ 變更自動部署"

echo ""
echo "✅ 完成後架構："
echo "  push code → GitHub Actions build image → 更新 k8s yaml → ArgoCD 自動部署"
