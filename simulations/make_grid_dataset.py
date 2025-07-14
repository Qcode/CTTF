from model.simulation import generate_days
from model.util import get_default_config

config = get_default_config()
config.TOTAL_USERS = 600
config.GRID_SIZE = 25
dataset = generate_days(config)
dataset.to_csv("datasets/grid.csv")
