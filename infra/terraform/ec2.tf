locals {
  ec2_name = "${var.environment}-policy-service"
}

resource "aws_instance" "app" {
  ami                    = var.ec2_ami_id
  instance_type          = var.ec2_instance_type
  subnet_id              = aws_subnet.public[0].id
  vpc_security_group_ids = [aws_security_group.ec2.id]
  iam_instance_profile   = aws_iam_instance_profile.ec2.name
  key_name               = var.ec2_ssh_key_name

  tags = { Name = local.ec2_name }

  user_data = templatefile("${path.module}/user-data.sh", {
    ecr_repository      = aws_ecr_repository.app.repository_url
    aws_region          = var.aws_region
    db_host             = aws_db_instance.main.address
    db_port             = aws_db_instance.main.port
    db_name             = aws_db_instance.main.db_name
    db_user             = aws_db_instance.main.username
    db_password_secret  = aws_secretsmanager_secret.db_password.name
    jwt_secret_secret   = aws_secretsmanager_secret.jwt_secret.name
    rabbitmq_host       = var.rabbitmq_host != null ? var.rabbitmq_host : ""
    redis_host          = var.redis_host != null ? var.redis_host : ""
    zipkin_url          = var.zipkin_url != null ? var.zipkin_url : ""
  })
}

resource "aws_ssm_parameter" "ec2_instance_id" {
  name  = "/${var.environment}/policy-service/ec2-instance-id"
  type  = "String"
  value = aws_instance.app.id
}
