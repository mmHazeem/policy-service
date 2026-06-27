terraform {
  required_version = ">= 1.6"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  backend "s3" {
    # Provide via -backend-config or init manually
    # bucket = "policy-service-tfstate"
    # key    = "terraform.tfstate"
    # region = "eu-central-1"
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "policy-service"
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}
