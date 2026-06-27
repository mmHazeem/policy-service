resource "aws_secretsmanager_secret" "jwt_secret" {
  name = "${var.environment}-policy-service-jwt-secret"
}

resource "random_password" "jwt" {
  length  = 64
  special = false
}

resource "aws_secretsmanager_secret_version" "jwt_secret" {
  secret_id     = aws_secretsmanager_secret.jwt_secret.id
  secret_string = random_password.jwt.result
}
