#!/bin/bash
# ================================================================
# momo-microservices 完整部署腳本
# 使用方式：bash deploy.sh 你的GitHub帳號 你的GitHub_Token
# ================================================================
set -e

GITHUB_USER=${1:?"請提供 GitHub 帳號: bash deploy.sh 帳號 token"}
GITHUB_TOKEN=${2:?"請提供 GitHub Token: bash deploy.sh 帳號 token"}

echo "================================================"
echo "  momo-microservices K8s 部署"
echo "  GitHub: ${GITHUB_USER}"
echo "================================================"
echo ""

# ── Step 1: 確認 K8s ─────────────────────────────
echo "🔍 Step 1: 確認 K8s 連線..."
kubectl cluster-info > /dev/null 2>&1 || {
  echo "❌ K8s 未啟動！請開啟 Docker Desktop → Settings → Kubernetes → Enable"
  exit 1
}
echo "✅ K8s 連線正常"
echo ""

# ── Step 2: 換掉 GitHub 帳號 ─────────────────────
echo "📝 Step 2: 更新 GitHub 帳號到 K8s YAML..."
find k8s/ argocd/ -name "*.yaml" -exec \
  sed -i "s|YOUR_GITHUB_USERNAME|${GITHUB_USER}|g; s|YOUR_GITHUB|${GITHUB_USER}|g" {} \;
echo "✅ 已更新所有 YAML"
echo ""

# ── Step 3: Build JAR ────────────────────────────
echo "🔨 Step 3: Build Spring Boot JAR..."
for svc in user-service order-service shipping-service; do
  echo "  Building ${svc}..."
  cd $svc && mvn clean package -DskipTests -q && cd ..
  echo "  ✅ ${svc} built"
done
echo ""

# ── Step 4: Build & Push Docker Images ───────────
echo "🐳 Step 4: Build & Push Docker images to ghcr.io..."
echo "${GITHUB_TOKEN}" | docker login ghcr.io -u ${GITHUB_USER} --password-stdin

for svc in user-service order-service shipping-service; do
  IMAGE="ghcr.io/${GITHUB_USER}/momo-microservices/${svc}:latest"
  echo "  Building ${IMAGE}..."
  docker build -t ${IMAGE} ./${svc} -q
  echo "  Pushing ${IMAGE}..."
  docker push ${IMAGE} -q
  echo "  ✅ ${svc} pushed"
done
echo ""

# ── Step 5: 建立 K8s Namespace 和 Secret ─────────
echo "🔐 Step 5: 建立 Namespace 和 Secrets..."
kubectl apply -f k8s/base/namespace.yaml

# imagePullSecret for ghcr.io
kubectl create secret docker-registry ghcr-secret \
  --docker-server=ghcr.io \
  --docker-username=${GITHUB_USER} \
  --docker-password=${GITHUB_TOKEN} \
  --namespace=momo-system \
  --dry-run=client -o yaml | kubectl apply -f -

echo "✅ Secrets 建立完成"
echo ""

# ── Step 6: 部署基礎設施 ─────────────────────────
echo "🏗️  Step 6: 部署 MySQL / Redis / Kafka / Keycloak..."
kubectl apply -k k8s/base/

echo "⏳ 等待 MySQL 啟動（約 60 秒）..."
kubectl wait --for=condition=ready pod -l app=mysql-user \
  -n momo-system --timeout=120s || echo "⚠️  mysql-user 還在啟動中，繼續..."
kubectl wait --for=condition=ready pod -l app=mysql-order \
  -n momo-system --timeout=120s || echo "⚠️  mysql-order 還在啟動中，繼續..."
kubectl wait --for=condition=ready pod -l app=mysql-shipping \
  -n momo-system --timeout=120s || echo "⚠️  mysql-shipping 還在啟動中，繼續..."
echo ""

# ── Step 7: 安裝 ArgoCD ──────────────────────────
echo "🚀 Step 7: 安裝 ArgoCD..."
kubectl create namespace argocd --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -n argocd \
  -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml -q

echo "⏳ 等待 ArgoCD 啟動（約 60 秒）..."
kubectl wait --for=condition=available deployment/argocd-server \
  -n argocd --timeout=180s
echo "✅ ArgoCD 啟動完成"
echo ""

# ── Step 8: 部署 App of Apps ─────────────────────
echo "📋 Step 8: 部署 ArgoCD App of Apps..."
kubectl apply -f argocd/app-of-apps.yaml
echo "✅ ArgoCD App of Apps 部署完成"
echo ""

# ── Step 9: 部署 Observability ───────────────────
echo "📊 Step 9: 部署 Observability Stack..."
kubectl apply -k k8s/observability/
echo "✅ Prometheus + Grafana + ELK + Jaeger 部署完成"
echo ""

# ── Step 10: 取得 ArgoCD 密碼 ────────────────────
echo "================================================"
echo "  ✅ 部署完成！"
echo "================================================"
echo ""
echo "🔑 ArgoCD 初始密碼："
kubectl -n argocd get secret argocd-initial-admin-secret \
  -o jsonpath='{.data.password}' | base64 -d
echo ""
echo ""
echo "🌐 存取方式（各開一個終端機）："
echo ""
echo "  ArgoCD UI:"
echo "  kubectl port-forward svc/argocd-server -n argocd 8443:443"
echo "  → https://localhost:8443 (admin / 上面的密碼)"
echo ""
echo "  API Gateway:"
echo "  kubectl port-forward svc/nginx 80:80 -n momo-system"
echo "  → http://localhost/api/orders"
echo ""
echo "  Grafana:"
echo "  kubectl port-forward svc/grafana 3000:3000 -n momo-system"
echo "  → http://localhost:3000 (admin/admin)"
echo ""
echo "  Kibana:"
echo "  kubectl port-forward svc/kibana 5601:5601 -n momo-system"
echo "  → http://localhost:5601"
echo ""
echo "  Jaeger:"
echo "  kubectl port-forward svc/jaeger 16686:16686 -n momo-system"
echo "  → http://localhost:16686"
echo ""
echo "📦 查看所有 Pod 狀態："
echo "  kubectl get pods -n momo-system"
