import papermill as pm
import os

if not os.path.isdir("output-notebooks"):
    os.mkdir("output-notebooks")

for strategy in ("PIECEWISE", "OPPOSITE"):
    for adversary in (0, 0.01, 0.02, 0.05, 0.1, 0.25):
        pm.execute_notebook(
            "simulations.ipynb",
            f"output-notebooks/a{adversary}-s{strategy}.ipynb",
            parameters = {
                "ADVERSARY_STRATEGY": strategy,
                "ADVERSARY_PERCENT": adversary,
                "LEECH_PERCENT": 0.75 - adversary
            }
        )
