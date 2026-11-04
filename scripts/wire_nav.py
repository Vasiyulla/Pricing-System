path = 'app/src/main/java/com/example/MainActivity.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

target = """                onNavigateToStore = { storeId ->
                    navController.navigate(Routes.storeDetail(storeId))
                }"""

replacement = """                onNavigateToStore = { storeId ->
                    navController.navigate(Routes.storeDetail(storeId))
                },
                onNavigateToTrolleyEar = {
                    navController.navigate(Routes.TROLLEY_EAR)
                },
                onNavigateToTwoStopBasket = {
                    navController.navigate(Routes.TWO_STOP_BASKET)
                },
                onNavigateToYellowStickers = {
                    navController.navigate(Routes.YELLOW_STICKERS)
                },
                onNavigateToBounties = {
                    navController.navigate(Routes.BOUNTIES)
                }"""

if target in content:
    content = content.replace(target, replacement)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)
    print("Nav wiring succeeded")
else:
    print("Target not found")

