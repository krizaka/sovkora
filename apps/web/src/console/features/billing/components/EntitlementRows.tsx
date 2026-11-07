import { Button, Input } from "@krizaka/orazaka-design-system";
import { type Entitlement } from "@krizaka/orazaka-shared";

/** The « Droits » rows shared by the pack and the plan editors: a key, a value type and a value each, and « Ajouter un droit ». */
export function EntitlementRows({
  entitlements,
  onChange,
}: Readonly<{ entitlements: Entitlement[]; onChange: (next: Entitlement[]) => void }>) {
  const setEntitlement = (index: number, next: Entitlement) => {
    const copy = [...entitlements];
    copy[index] = next;
    onChange(copy);
  };

  return (
    <>
      {entitlements.map((entitlement, index) => (
        <div key={index} className="grid gap-2 sm:grid-cols-3">
          <Input
            value={entitlement.key}
            onChange={(e) => setEntitlement(index, { ...entitlement, key: e.target.value })}
          />
          <Input
            value={entitlement.valueType}
            onChange={(e) =>
              setEntitlement(index, {
                ...entitlement,
                valueType: e.target.value as Entitlement["valueType"],
              })
            }
          />
          <Input
            value={entitlement.value}
            onChange={(e) => setEntitlement(index, { ...entitlement, value: e.target.value })}
          />
        </div>
      ))}
      <Button
        size="sm"
        variant="ghost"
        onClick={() => onChange([...entitlements, { key: "", valueType: "boolean", value: "true" }])}
      >
        Ajouter un droit
      </Button>
    </>
  );
}
