import os
import pickle
from model.util import get_default_config

if not os.path.isdir("configs"):
    os.mkdir("configs")

config_num = 0

config = get_default_config()
config.NUM_REGULAR = 6250
config.NUM_ADVERSARY = 0
config.NUM_LEECH = 18750

for i in [1, 2, 3, 7]:
    config.PREBLACKOUT_DAYS = list(range(1, i + 1))
    config.POSTBLACKOUT_DAYS = list(range(i + 1, (i + 1) + (2 * 7) + 1))
    config.REQUEST_CUTOFF_DAY = i + 7 + 1
    with open(f"configs/config{config_num}.pkl", "wb") as f:
        pickle.dump(config, f)
    config_num += 1

config = get_default_config()
config.NUM_ADVERSARY = 0

for i in [22500, 18750, 12500, 6250, 2500, 1250, 250]:
    config.NUM_REGULAR = i
    config.NUM_LEECH = 25000 - i
    with open(f"configs/config{config_num}.pkl", "wb") as f:
        pickle.dump(config, f)
    config_num += 1

config = get_default_config()
table = [
    (500, 6125, 18375),
    (1250, 5938, 17812),
    (2500, 5625, 16875),
    (6250, 4688, 14062),
]
for entry in table:
    assert entry[0] + entry[1] + entry[2] == 25000
    config.NUM_ADVERSARY = entry[0]
    config.NUM_REGULAR = entry[1]
    config.NUM_LEECH = entry[2]
    with open(f"configs/config{config_num}.pkl", "wb") as f:
        pickle.dump(config, f)
    config_num += 1
