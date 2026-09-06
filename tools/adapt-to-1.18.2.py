import re
import sys

def adapt():
    path = "src/main/java/com/dwurdy/heaphammer/command/HeapHammerCommands.java"
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()

    # Add TextComponent import if missing
    if "TextComponent" not in content:
        content = content.replace(
            "import net.minecraft.network.chat.Component;",
            "import net.minecraft.network.chat.Component;\nimport net.minecraft.network.chat.TextComponent;"
        )

    # Replace Component.literal(...) with new TextComponent(...)
    content = content.replace("Component.literal(", "new TextComponent(")

    # Replace ctx.getSource().sendSuccess(() -> expr, flag); with ctx.getSource().sendSuccess(expr, flag);
    # Handles multiline lambdas and expressions
    pattern = re.compile(r"ctx\.getSource\(\)\.sendSuccess\(\s*\(\)\s*->\s*(.*?),\s*(true|false)\s*\);", re.DOTALL)
    
    def repl(m):
        expr = m.group(1).strip()
        flag = m.group(2).strip()
        return f"ctx.getSource().sendSuccess({expr}, {flag});"

    content = pattern.sub(repl, content)

    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print("Successfully adapted HeapHammerCommands.java for 1.18.2")

if __name__ == "__main__":
    adapt()
