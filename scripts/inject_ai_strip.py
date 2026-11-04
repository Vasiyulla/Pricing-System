import os

path = 'app/src/main/java/com/example/feature/home/HomeScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

target = "// Categories Horizontal Filter Strip"

injection = """// ── AI Superpowers Quick Access Strip ──
                        item(key = "ai_superpowers_strip") {
                            Column(modifier = Modifier.padding(horizontal = MaterialTheme.spacing.spaceMd)) {
                                Text(
                                    text = "AI Smart Tools",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    item {
                                        Surface(
                                            onClick = onNavigateToTrolleyEar,
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color(0xFFE8F5E9),
                                            border = BorderStroke(1.dp, Color(0xFF81C784))
                                        ) {
                                            Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Headphones, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(20.dp))
                                                Spacer(Modifier.width(8.dp))
                                                Column {
                                                    Text("Trolley Ear", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1B5E20))
                                                    Text("In-Store Audio", fontSize = 11.sp, color = Color(0xFF2E7D32))
                                                }
                                            }
                                        }
                                    }
                                    item {
                                        Surface(
                                            onClick = onNavigateToTwoStopBasket,
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color(0xFFE3F2FD),
                                            border = BorderStroke(1.dp, Color(0xFF90CAF9))
                                        ) {
                                            Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Savings, contentDescription = null, tint = Color(0xFF1565C0), modifier = Modifier.size(20.dp))
                                                Spacer(Modifier.width(8.dp))
                                                Column {
                                                    Text("2-Stop Split", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0D47A1))
                                                    Text("Pareto Basket", fontSize = 11.sp, color = Color(0xFF1565C0))
                                                }
                                            }
                                        }
                                    }
                                    item {
                                        Surface(
                                            onClick = onNavigateToYellowStickers,
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color(0xFFFFF9C4),
                                            border = BorderStroke(1.dp, Color(0xFFFFF176))
                                        ) {
                                            Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.LocalOffer, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(20.dp))
                                                Spacer(Modifier.width(8.dp))
                                                Column {
                                                    Text("Yellow Stickers", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFBF360C))
                                                    Text("Clearance Deals", fontSize = 11.sp, color = Color(0xFFE65100))
                                                }
                                            }
                                        }
                                    }
                                    item {
                                        Surface(
                                            onClick = onNavigateToBounties,
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color(0xFFF3E5F5),
                                            border = BorderStroke(1.dp, Color(0xFFCE93D8))
                                        ) {
                                            Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.MilitaryTech, contentDescription = null, tint = Color(0xFF6A1B9A), modifier = Modifier.size(20.dp))
                                                Spacer(Modifier.width(8.dp))
                                                Column {
                                                    Text("AI Bounties", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF4A148C))
                                                    Text("Earn Points", fontSize = 11.sp, color = Color(0xFF6A1B9A))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Categories Horizontal Filter Strip"""

if target in content:
    content = content.replace(target, injection)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)
    print("Injection succeeded")
else:
    print("Target not found")

