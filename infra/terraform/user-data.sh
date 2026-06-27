#!/bin/bash
set -euo pipefail

# Install Docker
if ! command -v docker &>/dev/null; then
  dnf install -y docker
  systemctl enable --now docker
fi

# Install AWS CLI
if ! command -v aws &>/dev/null; then
  dnf install -y awscli
fi

# Install jq
if ! command -v jq &>/dev/null; then
  dnf install -y jq
fi

# Write deploy wrapper
# Terraform substitutes ${name} for infra values.
# Shell variables use $$ to escape — Terraform passes $$ as literal $.
cat > /usr/local/bin/deploy-policy-service.sh <<'SCRIPT'
#!/bin/bash
set -euo pipefail

ECR_REPO="${ecr_repository}"
AWS_REGION="${aws_region}"
DB_HOST="${db_host}"
DB_PORT="${db_port}"
DB_NAME="${db_name}"
DB_USER="${db_user}"
DB_PASSWORD_SECRET="${db_password_secret}"
JWT_SECRET_SECRET="${jwt_secret_secret}"
RABBITMQ_HOST="${rabbitmq_host}"
REDIS_HOST="${redis_host}"
ZIPKIN_URL="${zipkin_url}"

DB_PASSWORD=$(aws secretsmanager get-secret-value \
  --secret-id "$${DB_PASSWORD_SECRET}" --region "$${AWS_REGION}" \
  --query SecretString --output text)
JWT_SECRET=$(aws secretsmanager get-secret-value \
  --secret-id "$${JWT_SECRET_SECRET}" --region "$${AWS_REGION}" \
  --query SecretString --output text)

aws ecr get-login-password --region "$${AWS_REGION}" | \
  docker login --username AWS --password-stdin "$${ECR_REPO}"

docker pull "$${ECR_REPO}:latest"
docker rm -f policy-service 2>/dev/null || true

docker run -d \
  --name policy-service \
  --restart unless-stopped \
  -p 8080:8080 \
  -e SPRING_DATASOURCE_URL="jdbc:postgresql://$${DB_HOST}:$${DB_PORT}/$${DB_NAME}" \
  -e SPRING_DATASOURCE_USERNAME="$${DB_USER}" \
  -e SPRING_DATASOURCE_PASSWORD="$${DB_PASSWORD}" \
  -e JWT_SECRET="$${JWT_SECRET}" \
  -e SPRING_PROFILES_ACTIVE="docker" \
  $$( [ -n "$${RABBITMQ_HOST}" ] && echo "-e SPRING_RABBITMQ_HOST=$${RABBITMQ_HOST}" ) \
  $$( [ -n "$${REDIS_HOST}" ] && echo "-e SPRING_REDIS_HOST=$${REDIS_HOST}" ) \
  $$( [ -n "$${ZIPKIN_URL}" ] && echo "-e ZIPKIN_URL=$${ZIPKIN_URL}" ) \
  "$${ECR_REPO}:latest"

docker image prune -f
SCRIPT

chmod +x /usr/local/bin/deploy-policy-service.sh

# Run initial deploy
/usr/local/bin/deploy-policy-service.sh
