# infra/terraform/main.tf

# ==============================================================================
# STEP 1: Provision Serverless GPU Workers
# ==============================================================================

module "compute_runpod" {
  source = "./modules/compute-runpod"

  api_key         = var.runpod_api_key
  gpu_type        = var.runpod_gpu_type
  min_replicas    = var.runpod_min_replicas
  max_replicas    = var.runpod_max_replicas
  container_image = var.runpod_container_image
}

module "compute_modal" {
  source = "./modules/compute-modal"

  api_token_id     = var.modal_api_token_id
  api_token_secret = var.modal_api_token_secret
  app_name         = "orazaka-inference"
}

# ==============================================================================
# STEP 2: Shielded Networking and Managed Stateful Services
# ==============================================================================

module "aws_vpc" {
  source = "./modules/aws-vpc"

  vpc_cidr           = var.vpc_cidr
  environment        = var.environment
  availability_zones = var.availability_zones
}

module "aws_brokers" {
  source = "./modules/aws-brokers"

  vpc_id             = module.aws_vpc.vpc_id
  private_subnet_ids = module.aws_vpc.private_subnet_ids
  environment        = var.environment

  db_username       = var.db_username
  db_password       = var.db_password
  db_name           = "orazaka_db"
  pg_vector_enabled = true

  redis_node_type        = "cache.t4g.micro"
  rabbitmq_instance_type = "mq.t3.micro"
  rabbitmq_username      = var.rabbitmq_username
  rabbitmq_password      = var.rabbitmq_password

  depends_on = [module.aws_vpc]
}

# ==============================================================================
# STEP 3: ECS Fargate Application Deployment (downstream dependency injection)
# ==============================================================================

module "aws_compute_ecs" {
  source = "./modules/aws-compute-ecs"

  vpc_id             = module.aws_vpc.vpc_id
  public_subnet_ids  = module.aws_vpc.public_subnet_ids
  private_subnet_ids = module.aws_vpc.private_subnet_ids
  environment        = var.environment

  # Database Credentials injection from Step 2
  db_connection_url = "jdbc:postgresql://${module.aws_brokers.db_endpoint}/${module.aws_brokers.db_name}"
  db_username       = var.db_username
  db_password       = var.db_password

  # Cache & Job Queue coordinates from Step 2
  redis_url         = "redis://${module.aws_brokers.redis_endpoint}"
  rabbitmq_host     = module.aws_brokers.rabbitmq_host
  rabbitmq_username = var.rabbitmq_username
  rabbitmq_password = var.rabbitmq_password

  # GPU Serverless Endpoint URL injection from Step 1
  cloud_load_balancer_url = module.compute_runpod.endpoint_url
  modal_load_balancer_url = module.compute_modal.endpoint_url

  # Auth secrets
  google_client_id     = var.google_client_id
  google_client_secret = var.google_client_secret
  github_client_id     = var.github_client_id
  github_client_secret = var.github_client_secret
  nextauth_secret      = var.nextauth_secret

  # Automation Worker sidecar (Quartz + AMQP job scheduler)
  automation_worker_image = var.automation_worker_image

  depends_on = [
    module.aws_brokers,
    module.compute_runpod,
    module.compute_modal
  ]
}
