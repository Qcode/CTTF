import numpy as np
import pandas as pd
from tqdm import tqdm
from model.user import UserType, PageRequest


def generate_days(config):
    num_days = len(config.PREBLACKOUT_DAYS) + len(config.POSTBLACKOUT_DAYS)
    data = {
        "uid": np.repeat(np.arange(config.TOTAL_USERS), num_days * 48),
        "d": np.tile(np.repeat(np.arange(1, num_days + 1), 48), config.TOTAL_USERS),
        "t": np.tile(np.arange(48), config.TOTAL_USERS * num_days),
        "x": np.zeros(48 * num_days * config.TOTAL_USERS),
        "y": np.zeros(48 * num_days * config.TOTAL_USERS),
    }
    current_user = None
    for index in range(48 * num_days * config.TOTAL_USERS):
        if data["uid"][index] != current_user:
            current_user = data["uid"][index]
            data["x"][index] = np.random.randint(config.GRID_SIZE)
            data["y"][index] = np.random.randint(config.GRID_SIZE)
        else:
            while True:
                movement_x = np.random.randint(
                    -config.MOVEMENT_DISTANCE, config.MOVEMENT_DISTANCE + 1
                )
                movement_y = np.random.randint(
                    -config.MOVEMENT_DISTANCE, config.MOVEMENT_DISTANCE + 1
                )
                last_x = data["x"][index - 1]
                last_y = data["y"][index - 1]
                if (
                    last_x + movement_x < 0
                    or last_x + movement_x >= config.GRID_SIZE
                    or last_y + movement_y < 0
                    or last_y + movement_y >= config.GRID_SIZE
                ):
                    continue
                data["x"][index] = last_x + movement_x
                data["y"][index] = last_y + movement_y
                break
    return pd.DataFrame(data)


def simulate_pre_blackout(config, the_dataset, users):
    for day_index in config.PREBLACKOUT_DAYS:
        print(f"DAY {day_index}")
        day = the_dataset.loc[the_dataset["d"] == day_index]
        contact_groups = day.groupby(["x", "y", "t"])["uid"].apply(list)

        for time_step in tqdm(range(48), desc="Processing Time Steps"):
            for x in range(config.GRID_SIZE):
                for y in range(config.GRID_SIZE):
                    user_ids = contact_groups.get((x + 1, y + 1, time_step), [])
                    pairs = [
                        (user_ids[i], user_ids[j])
                        for i in range(len(user_ids))
                        for j in range(len(user_ids))
                        if i != j
                    ]
                    for pair in pairs:
                        updater_index = pair[0]
                        encountered_index = pair[1]
                        updater = users[updater_index]
                        encountered = users[encountered_index]
                        if updater.user_type == UserType.ADVERSARY:
                            continue

                        if encountered.preferences is not None:
                            if updater.computed_preferences is None:
                                updater.computed_preferences = (
                                    encountered.computed_preferences.copy()
                                )
                            else:
                                repeat = (
                                    config.ADVERSARY_FORCE_MULTIPLIER
                                    if encountered.user_type == UserType.ADVERSARY
                                    else 1
                                )
                                updater.num_rankings += encountered.one_hot_vector
                                updater.computed_preferences = (
                                    updater.computed_preferences
                                    + (
                                        encountered.preferences
                                        - updater.computed_preferences
                                    )
                                    .multiply(repeat)
                                    .multiply(updater.num_rankings.power(-1))
                                )


def simulate_post_blackout(config, the_dataset, users, the_truth_probability):
    for day_index in config.POSTBLACKOUT_DAYS:
        print(f"DAY {day_index}")
        day = the_dataset.loc[the_dataset["d"] == day_index]
        contact_groups = day.groupby(["x", "y", "t"])["uid"].apply(list)

        for time_step in tqdm(range(48), desc="Processing Time Steps"):

            valid_users = [
                user for user in users if user.user_type != UserType.ADVERSARY
            ]
            random_values = np.random.uniform(0, 1, len(valid_users))
            requesting_users = np.array(valid_users)[
                random_values < config.PAGE_REQUEST_PROBABILITY
            ]

            if len(requesting_users) > 0:
                page_choices = np.random.choice(
                    config.PAGE_COUNT,
                    size=len(requesting_users),
                    p=the_truth_probability,
                )
                requests = [PageRequest(choice, time_step) for choice in page_choices]

                for user, request in zip(requesting_users, requests):
                    user.requested_pages.append(request)

            for x in range(config.GRID_SIZE):
                for y in range(config.GRID_SIZE):
                    user_ids = contact_groups.get((x + 1, y + 1, time_step), [])
                    pairs = [
                        (user_ids[i], user_ids[j])
                        for i in range(len(user_ids))
                        for j in range(len(user_ids))
                        if i != j
                    ]
                    for pair in pairs:
                        requester_index = pair[0]
                        encountered_index = pair[1]
                        requester = users[requester_index]
                        encountered = users[encountered_index]
                        if (
                            requester.user_type == UserType.ADVERSARY
                            or encountered.user_type == UserType.ADVERSARY
                        ):
                            continue
                        forwarded = 0
                        for request in requester.requested_pages:
                            if forwarded == config.FORWARDING_LIMIT:
                                break
                            request.has_interacted = True
                            if request.is_resolved():
                                continue
                            if request.index in encountered.stored_pages:
                                forwarded += 1
                                request.resolve(time_step)
