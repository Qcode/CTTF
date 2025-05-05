import pickle
import numpy as np
from tqdm import tqdm

runs = [2, 3, 4, 5, 6, 7]

for run in runs:
    print(f"RUN {run}")
    with open(f"data/runs/{run}/config", "rb") as f:
        config = pickle.load(f)
    print("Opening users")
    with open(f"data/runs/{run}/users", "rb") as f:
        users = pickle.load(f)

    stored_set = set()

    total_requests = 0
    total_resolved = 0
    total_storing_first = 0
    total_with_interactions = 0

    total_was_rated = 0

    user_appearance_count = 0

    requests = np.zeros(config.PAGE_COUNT)
    satisfied = np.zeros(config.PAGE_COUNT)

    satisfied_by_time_initiated = np.zeros(48)
    total_by_time_initiated = np.zeros(48)

    total_unsatisfied = 0
    unsatisfied_by_index = np.zeros(config.PAGE_COUNT)

    total_satisfied = 0
    satisfied_by_index = np.zeros(config.PAGE_COUNT)

    satisfied_in_x_timesteps = np.zeros(48)

    for user in tqdm(users):
        stored_set.update(user.stored_pages)
        if 0 in user.stored_pages:
            total_storing_first += 1
        total_requests += len(user.requested_pages)
        for request in user.requested_pages:
            requests[request.index] += 1
            if request.has_interacted:
                total_with_interactions += 1
                total_by_time_initiated[request.started_timestep] += 1
            if request.is_resolved():
                # print(request.index)
                total_resolved += 1
                satisfied[request.index] += 1
                satisfied_by_time_initiated[request.started_timestep] += 1
                # print(request.started_timestep)
                # print(request.ended_timestep)
            if request.has_interacted and not request.is_resolved():
                total_unsatisfied += 1
                unsatisfied_by_index[request.index] += 1
            if request.has_interacted and request.is_resolved():
                total_satisfied += 1
                satisfied_by_index[request.index] += 1

                satisfied_in_x_timesteps[
                    request.ended_timestep - request.started_timestep
                ] += 1

    percent_satisfied_by_time_initiated = (
        satisfied_by_time_initiated / total_by_time_initiated
    )

    print(total_resolved / total_with_interactions)
    print(len(stored_set))
