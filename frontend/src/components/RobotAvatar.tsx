import robotSvg from "@/assets/robot.svg";

export interface RobotAvatarProps {
  size?: "sm" | "md" | "lg" | "xl";
  className?: string;
}

const sizes = {
  sm: "w-8 h-8",
  md: "w-12 h-12",
  lg: "w-24 h-24",
  xl: "w-48 h-48",
};

export function RobotAvatar({ size = "md", className = "" }: RobotAvatarProps) {
  return (
    <div className={`${sizes[size]} ${className}`}>
      <img
        src={robotSvg}
        alt="AI Negotiation Bot"
        className="w-full h-full"
      />
    </div>
  );
}
