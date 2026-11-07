# Run a Studio

A Studio is a ready-made workflow behind one form. You fill the form, Orazaka runs the steps, and you get a result and a run record.

## From the web app

1. Open **Studios** and pick one, for example image generation.
2. Fill the form. The page shows the price estimate before you run anything.
3. Press **Run**. The run appears in **Jobs** with its progress.
4. Open the result when it is ready. Its run id, duration and cost are listed beside it.

## From the terminal

```bash
npx orazaka studio list                     # the catalogue
npx orazaka studio install <studioKey>      # add one to your workspace
npx orazaka studio run <installation> --inputs key=value
npx orazaka studio logs <runId>             # each step and its result
```

Pipes make a list: `--inputs tags=a|b|c`. For text with commas, use `--inputs-file inputs.json`.

## Check before you spend

`POST /api/v1/studios/{studioKey}/policy-check` answers what the [content policy](CONTENT_POLICY.md) would do with your inputs. It holds no credits and calls no provider.

## Add your own

A pack is a folder with a `pack.yaml` and its blueprints. The [packs reference](_generated/PACKS.md) lists every manifest field and shows how to write, validate and install one.

## Next

- [Concepts](CONCEPTS.md): where a run fits in the engine.
- [Packs and Studios reference](_generated/PACKS.md): the full catalogue.
- [Jobs and schedules](_generated/JOBS.md): runs without a person waiting.
