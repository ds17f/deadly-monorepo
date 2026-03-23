output "droplet_ip" {
  value = digitalocean_droplet.server.ipv4_address
}

output "droplet_id" {
  value = digitalocean_droplet.server.id
}

output "alpha_ip" {
  value = digitalocean_droplet.alpha.ipv4_address
}

output "beta_ip" {
  value = digitalocean_droplet.beta.ipv4_address
}

output "prod_ip" {
  value = digitalocean_droplet.prod.ipv4_address
}
