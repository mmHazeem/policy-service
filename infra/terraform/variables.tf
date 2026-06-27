variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "eu-central-1"
}

variable "environment" {
  description = "Deployment environment"
  type        = string
  default     = "production"
}

variable "vpc_cidr" {
  description = "VPC CIDR block"
  type        = string
  default     = "10.0.0.0/16"
}

variable "public_subnet_cidrs" {
  description = "Public subnet CIDRs"
  type        = list(string)
  default     = ["10.0.1.0/24", "10.0.2.0/24"]
}

variable "private_subnet_cidrs" {
  description = "Private subnet CIDRs"
  type        = list(string)
  default     = ["10.0.10.0/24", "10.0.11.0/24"]
}

variable "availability_zones" {
  description = "AZs to deploy into"
  type        = list(string)
  default     = ["eu-central-1a", "eu-central-1b"]
}

variable "db_instance_class" {
  description = "RDS instance class"
  type        = string
  default     = "db.t4g.micro"
}

variable "db_allocated_storage" {
  description = "RDS allocated storage in GB"
  type        = number
  default     = 20
}

variable "ec2_instance_type" {
  description = "EC2 instance type"
  type        = string
  default     = "t3.micro"
}

variable "ec2_ssh_key_name" {
  description = "EC2 SSH key pair name (optional — use SSM Session Manager instead)"
  type        = string
  default     = null
}

variable "ec2_ami_id" {
  description = "EC2 AMI ID (default: Amazon Linux 2023 in eu-central-1)"
  type        = string
  default     = "ami-0e4031c41e1f7833f"
}

variable "allowed_cidr_blocks" {
  description = "CIDR blocks allowed to reach EC2 port 8080"
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "domain_name" {
  description = "Optional domain name for the service"
  type        = string
  default     = null
}

variable "rabbitmq_host" {
  description = "RabbitMQ host (set null to skip, or use Amazon MQ)"
  type        = string
  default     = null
}

variable "redis_host" {
  description = "Redis host (set null to skip, or use ElastiCache)"
  type        = string
  default     = null
}

variable "zipkin_url" {
  description = "Zipkin endpoint URL"
  type        = string
  default     = null
}
