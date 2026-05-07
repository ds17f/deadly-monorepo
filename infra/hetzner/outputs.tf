output "beta_ip" {
  value = hcloud_server.beta.ipv4_address
}

output "beta_ipv6" {
  value = hcloud_server.beta.ipv6_address
}

output "prod_ip" {
  value = hcloud_server.prod.ipv4_address
}

output "prod_ipv6" {
  value = hcloud_server.prod.ipv6_address
}
