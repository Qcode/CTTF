import papermill as pm
import os

if not os.path.isdir("output-notebooks"):
    os.mkdir("output-notebooks")

runs = [
    #(6250, 18750, 0), # 0%
    #(6125, 18375, 500), # 2%
    #(5938, 17812, 1250), # 5%
    #(5625, 16875, 2500), # 10%
    #(4688, 14062, 6250), # 25%
    (24500, 0, 500), # All proactive
    (18375, 6125, 500), # 75% proactive
    (12250, 12250, 500), # 50% proactive
    (2450, 22050, 500), #10% proactive
    (1225, 23275, 500) # 5% proactive
]

for run in runs:
    regular = run[0]
    leech = run[1]
    adversary = run[2]
    pm.execute_notebook(
        "simulations-2.ipynb",
        f"output-notebooks/r{regular}-l{leech}-a{adversary}.ipynb",
        parameters = {
            "NUM_REGULAR": regular,
            "NUM_LEECH": leech,
            "NUM_ADVERSARY": adversary
        }
    )
